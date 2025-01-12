/*
 * Copyright 2017-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.spring.autoconfigure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.google.cloud.resourcemanager.Project;
import com.google.cloud.resourcemanager.ResourceManager;
import com.google.cloud.spring.core.GcpEnvironment;
import com.google.cloud.spring.core.GcpEnvironmentProvider;
import com.google.cloud.spring.core.GcpProjectIdProvider;
import com.google.cloud.spring.core.MetadataProvider;
import com.google.cloud.spring.security.iap.AppEngineAudienceProvider;
import com.google.cloud.spring.security.iap.AudienceProvider;
import com.google.cloud.spring.security.iap.AudienceValidator;
import java.time.Instant;
import java.util.Collections;
import javax.servlet.http.HttpServletRequest;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;

/**
 * Tests for IAP auth config.
 *
 * @since 1.1
 */
@RunWith(MockitoJUnitRunner.class)
public class IapAuthenticationAutoConfigurationTests {

  /** used to check exception messages and types. */
  @Rule public ExpectedException expectedException = ExpectedException.none();

  static final String FAKE_USER_TOKEN = "lol cats forever";

  private ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  IapAuthenticationAutoConfiguration.class, TestConfiguration.class));

  @Mock HttpServletRequest mockIapRequest;

  @Mock HttpServletRequest mockNonIapRequest;

  @Mock static Jwt mockJwt;

  @Mock static GcpProjectIdProvider mockProjectIdProvider;

  @Mock static GcpEnvironmentProvider mockEnvironmentProvider;

  @Mock static ResourceManager mockResourceManager;

  @Mock static Project mockProject;

  @Mock static MetadataProvider mockMetadataProvider;

  @Before
  public void httpRequestSetup() {
    when(this.mockIapRequest.getHeader("x-goog-iap-jwt-assertion")).thenReturn("very fake jwt");
  }

  @Test
  public void testIapAutoconfiguredBeansExistInContext() {
    this.contextRunner
        .withPropertyValues("spring.cloud.gcp.security.iap.audience=unused")
        .run(this::verifyJwtBeans);
  }

  @Test
  public void testAutoconfiguredBeansMissingWhenGatingPropertyFalse() {

    this.expectedException.expect(NoSuchBeanDefinitionException.class);
    this.expectedException.expectMessage(
        "No qualifying bean of type "
            + "'org.springframework.security.oauth2.jwt.JwtDecoder' available");

    this.contextRunner
        .withPropertyValues("spring.cloud.gcp.security.iap.enabled=false")
        .run(context -> context.getBean(JwtDecoder.class));
  }

  @Test
  public void testIapBeansReturnedWhenBothIapAndSpringSecurityConfigPresent() {
    new ApplicationContextRunner()
        .withPropertyValues("spring.cloud.gcp.security.iap.audience=unused")
        .withConfiguration(
            AutoConfigurations.of(
                IapAuthenticationAutoConfiguration.class,
                OAuth2ResourceServerAutoConfiguration.class,
                TestConfiguration.class))
        .run(this::verifyJwtBeans);
  }

  @Test
  public void testIapBeansReturnedWhenBothIapWithMultipleAudiencesAndSpringSecurityConfigPresent() {
    when(mockJwt.getAudience()).thenReturn(Collections.singletonList("aud1"));

    this.contextRunner
        .withPropertyValues("spring.cloud.gcp.security.iap.audience=aud1, aud2")
        .run(
            context -> {
              AudienceValidator validator = context.getBean(AudienceValidator.class);
              OAuth2TokenValidatorResult result = validator.validate(mockJwt);
              assertThat(result.hasErrors()).isFalse();
            });
  }

  @Test
  public void testUserBeansReturnedUserConfigPresent() {
    this.contextRunner
        .withUserConfiguration(UserConfiguration.class)
        .withPropertyValues("spring.cloud.gcp.security.iap.audience=unused")
        .run(
            context -> {
              JwtDecoder jwtDecoder = context.getBean(JwtDecoder.class);
              assertThat(jwtDecoder).isNotNull();
              assertThat(jwtDecoder).isNotInstanceOf(NimbusJwtDecoder.class);
              assertThat(jwtDecoder.decode("Ceci n'est pas un Jwt")).isSameAs(mockJwt);

              BearerTokenResolver resolver = context.getBean(BearerTokenResolver.class);
              assertThat(resolver).isNotNull();
              assertThat(resolver.resolve(this.mockIapRequest)).isEqualTo(FAKE_USER_TOKEN);
              assertThat(resolver.resolve(this.mockNonIapRequest)).isEqualTo(FAKE_USER_TOKEN);
            });
  }

  @Test
  public void testCustomPropertyOverridesDefault() {
    this.contextRunner
        .withPropertyValues("spring.cloud.gcp.security.iap.header=some-other-header")
        .withPropertyValues("spring.cloud.gcp.security.iap.audience=unused")
        .run(
            context -> {
              when(this.mockNonIapRequest.getHeader("some-other-header"))
                  .thenReturn("other header jwt");

              BearerTokenResolver resolver = context.getBean(BearerTokenResolver.class);
              assertThat(resolver).isNotNull();
              assertThat(resolver.resolve(this.mockIapRequest)).isNull();
              assertThat(resolver.resolve(this.mockNonIapRequest)).isEqualTo("other header jwt");
            });
  }

  @Test
  public void testContextFailsWhenAudienceValidatorNotAvailable() throws Exception {

    this.contextRunner.run(
        context -> {
          assertThat(context)
              .getFailure()
              .hasCauseInstanceOf(NoSuchBeanDefinitionException.class)
              .hasMessageContaining(
                  "No qualifying bean of type"
                      + " 'com.google.cloud.spring.security.iap.AudienceProvider'");
        });
  }

  @Test
  public void testFixedStringAudienceValidatorAddedWhenAvailable() throws Exception {
    when(mockJwt.getExpiresAt()).thenReturn(Instant.now().plusSeconds(10));
    when(mockJwt.getNotBefore()).thenReturn(Instant.now().minusSeconds(10));

    this.contextRunner
        .withUserConfiguration(FixedAudienceValidatorConfiguration.class)
        .run(
            context -> {
              DelegatingOAuth2TokenValidator validator =
                  context.getBean(
                      "iapJwtDelegatingValidator", DelegatingOAuth2TokenValidator.class);
              OAuth2TokenValidatorResult result = validator.validate(mockJwt);
              assertThat(result.hasErrors()).isTrue();
              assertThat(result.getErrors()).hasSize(2);
              assertThat(result.getErrors().stream().map(error -> error.getDescription()))
                  .containsExactlyInAnyOrder(
                      "The iss claim is not valid",
                      "This aud claim is not equal to the configured audience");
            });
  }

  @Test
  public void testAppEngineAudienceValidatorAddedWhenAvailable() {
    when(this.mockEnvironmentProvider.getCurrentEnvironment())
        .thenReturn(GcpEnvironment.APP_ENGINE_FLEXIBLE);

    this.contextRunner
        .withUserConfiguration(FixedAudienceValidatorConfiguration.class)
        .run(
            context -> {
              AudienceProvider audienceProvider = context.getBean(AudienceProvider.class);
              assertThat(audienceProvider).isNotNull();
              assertThat(audienceProvider).isInstanceOf(AppEngineAudienceProvider.class);
            });
  }

  private void verifyJwtBeans(AssertableApplicationContext context) {
    JwtDecoder jwtDecoder = context.getBean(JwtDecoder.class);
    assertThat(jwtDecoder).isNotNull();
    assertThat(jwtDecoder).isInstanceOf(NimbusJwtDecoder.class);

    BearerTokenResolver resolver = context.getBean(BearerTokenResolver.class);
    assertThat(resolver).isNotNull();
    assertThat(resolver.resolve(this.mockIapRequest)).isEqualTo("very fake jwt");

    assertThat(resolver.resolve(this.mockNonIapRequest)).isNull();
  }

  /** Spring config for tests. */
  @Configuration
  static class UserConfiguration {

    @Bean
    public JwtDecoder jwtDecoder() {
      return s -> mockJwt;
    }

    @Bean
    public BearerTokenResolver bearerTokenResolver() {
      return httpServletRequest -> FAKE_USER_TOKEN;
    }
  }

  /** Spring config for tests. */
  @Configuration
  @AutoConfigureBefore(IapAuthenticationAutoConfiguration.class)
  static class TestConfiguration {

    @Bean
    static GcpProjectIdProvider mockProjectIdProvider() {
      return mockProjectIdProvider;
    }

    @Bean
    static GcpEnvironmentProvider mockEnvironmentProvider() {
      return mockEnvironmentProvider;
    }
  }

  /** Spring config for tests. */
  @Configuration
  @AutoConfigureBefore(IapAuthenticationAutoConfiguration.class)
  static class FixedAudienceValidatorConfiguration {

    @Bean
    AudienceValidator audienceValidator() {
      return new AudienceValidator(() -> "right audience");
    }
  }
}
