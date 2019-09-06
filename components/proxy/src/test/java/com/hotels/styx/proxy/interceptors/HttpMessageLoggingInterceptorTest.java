/*
  Copyright (C) 2013-2019 Expedia Inc.

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
 */
package com.hotels.styx.proxy.interceptors;

import com.hotels.styx.api.Eventual;
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.HttpVersion;
import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.api.LiveHttpResponse;
import com.hotels.styx.common.format.HttpMessageFormatter;
import com.hotels.styx.server.HttpInterceptorContext;
import com.hotels.styx.support.matchers.LoggingTestSupport;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import reactor.core.publisher.Mono;

import static ch.qos.logback.classic.Level.INFO;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static com.hotels.styx.api.LiveHttpRequest.get;
import static com.hotels.styx.api.LiveHttpResponse.response;
import static com.hotels.styx.api.RequestCookie.requestCookie;
import static com.hotels.styx.api.ResponseCookie.responseCookie;
import static com.hotels.styx.support.matchers.LoggingEventMatcher.loggingEvent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

public class HttpMessageLoggingInterceptorTest {

    private static final String FORMATTED_REQUEST = "request";
    private static final String FORMATTED_RESPONSE = "response";

    private LoggingTestSupport responseLogSupport;
    private HttpMessageLoggingInterceptor interceptor;

    @Mock
    private HttpMessageFormatter httpMessageFormatter;

    @BeforeClass
    public void setup() {
        MockitoAnnotations.initMocks(this);
        when(httpMessageFormatter.formatRequest(any(LiveHttpRequest.class))).thenReturn(FORMATTED_REQUEST);
        when(httpMessageFormatter.formatResponse(any(LiveHttpResponse.class))).thenReturn(FORMATTED_RESPONSE);
    }

    @BeforeMethod
    public void before() {
        responseLogSupport = new LoggingTestSupport("com.hotels.styx.http-messages.inbound");
        interceptor = new HttpMessageLoggingInterceptor(true, httpMessageFormatter);
    }

    @AfterMethod
    public void after() {
        responseLogSupport.stop();
    }

    @Test
    public void logsRequestsAndResponses() {
        LiveHttpRequest request = get("/")
                .version(HttpVersion.HTTP_1_1)
                .header("ReqHeader", "ReqHeaderValue")
                .cookies(requestCookie("ReqCookie", "ReqCookieValue"))
                .build();

        consume(interceptor.intercept(request, chain(
                response(OK)
                .header("RespHeader", "RespHeaderValue")
                .cookies(responseCookie("RespCookie", "RespCookieValue").build())
        )));

        assertThat(responseLogSupport.log(), contains(
                loggingEvent(INFO, "requestId=" + request.id() + ", request=" + FORMATTED_REQUEST + ", secure=true, origin=null"),
                loggingEvent(INFO, "requestId=" + request.id() + ", response=" + FORMATTED_RESPONSE +  ", secure=true")));
    }

    @Test
    public void logsRequestsAndResponsesShort() {
        interceptor = new HttpMessageLoggingInterceptor(false, httpMessageFormatter);
        LiveHttpRequest request = get("/")
                .header("ReqHeader", "ReqHeaderValue")
                .cookies(requestCookie("ReqCookie", "ReqCookieValue"))
                .build();

        consume(interceptor.intercept(request, chain(
                response(OK)
                        .header("RespHeader", "RespHeaderValue")
                        .cookies(responseCookie("RespCookie", "RespCookieValue").build())
        )));

        String requestPattern = "request=LiveHttpRequest\\{version=HTTP/1.1, method=GET, url=/, id=" + request.id() + "\\}";
        String responsePattern = "response=LiveHttpResponse\\{version=HTTP/1.1, status=200 OK\\}";

        assertThat(responseLogSupport.log(), contains(
                loggingEvent(INFO, "requestId=" + request.id() + ", " + requestPattern + ", secure=true, origin=null"),
                loggingEvent(INFO, "requestId=" + request.id() + ", " + responsePattern +  ", secure=true")));
    }

    @Test
    public void logsSecureRequests() {
        LiveHttpRequest request = get("/")
                .header("ReqHeader", "ReqHeaderValue")
                .cookies(requestCookie("ReqCookie", "ReqCookieValue"))
                .build();

        consume(interceptor.intercept(request, chain(response(OK))));

        assertThat(responseLogSupport.log(), contains(
                loggingEvent(INFO, "requestId=" + request.id() + ", request=" + FORMATTED_REQUEST + ", secure=true, origin=null"),
                loggingEvent(INFO, "requestId=" + request.id() + ", response=" + FORMATTED_RESPONSE +  ", secure=true")));
    }


    private static HttpInterceptor.Chain chain(LiveHttpResponse.Builder resp) {
        return new HttpInterceptor.Chain() {
            @Override
            public Eventual<LiveHttpResponse> proceed(LiveHttpRequest request) {
                return Eventual.of(resp.build());
            }

            @Override
            public HttpInterceptor.Context context() {
                return new HttpInterceptorContext(true);
            }
        };
    }

    private static void consume(Eventual<LiveHttpResponse> resp) {
        Mono.from(resp.flatMap(it -> it.aggregate(1000000))).block();
    }
}