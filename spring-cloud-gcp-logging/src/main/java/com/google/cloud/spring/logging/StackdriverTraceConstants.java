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

package com.google.cloud.spring.logging;

import org.springframework.util.Assert;

/** Constants for Stackdriver Trace. */
public interface StackdriverTraceConstants {

  /** The JSON field name for the log level (severity). */
  String SEVERITY_ATTRIBUTE = "severity";

  /** The JSON field name for the seconds of the timestamp. */
  String TIMESTAMP_SECONDS_ATTRIBUTE = "timestampSeconds";

  /** The JSON field name for the nanos of the timestamp. */
  String TIMESTAMP_NANOS_ATTRIBUTE = "timestampNanos";

  /** The JSON field name for the span-id. */
  String SPAN_ID_ATTRIBUTE = "logging.googleapis.com/spanId";

  /** The JSON field name for the trace-id. */
  String TRACE_ID_ATTRIBUTE = "logging.googleapis.com/trace";

  /** The name of the MDC parameter Spring Sleuth is storing the trace id at. */
  String MDC_FIELD_TRACE_ID = "traceId";

  /** The name of the MDC parameter Spring Sleuth is storing the span id at. */
  String MDC_FIELD_SPAN_ID = "spanId";

  /** The name of the MDC parameter Spring Sleuth is storing the span export information at. */
  String MDC_FIELD_SPAN_EXPORT = "X-Span-Export";

  /**
   * The JSON field name for the service context.
   *
   * @since 1.2
   */
  String SERVICE_CONTEXT_ATTRIBUTE = "serviceContext";

  /**
   * Composes the full trace name as expected by the Google Developers Console log viewer, to enable
   * trace correlation with log entries.
   *
   * @param projectId the GCP project ID
   * @param traceId the trace ID
   * @return the trace name in the "projects/[GCP_PROJECT_ID]/trace/[TRACE_ID]" format
   */
  static String composeFullTraceName(String projectId, String traceId) {
    Assert.notNull(projectId, "The project ID can't be null.");
    Assert.notNull(traceId, "The trace ID can't be null.");
    return "projects/" + projectId + "/traces/" + traceId;
  }
}
