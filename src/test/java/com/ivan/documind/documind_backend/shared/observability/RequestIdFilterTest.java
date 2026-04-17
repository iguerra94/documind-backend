package com.ivan.documind.documind_backend.shared.observability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.http.HttpServletResponse;

class RequestIdFilterTest {
  private final RequestIdFilter filter = new RequestIdFilter();

  @Test
  void generatesRequestIdWhenHeaderIsMissing() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/questions");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, (servletRequest, servletResponse) -> {
    });

    String requestId = response.getHeader(RequestId.HEADER_NAME);

    assertThat(requestId).isNotBlank();
    assertThat(RequestId.isValid(requestId)).isTrue();
    assertThat(request.getAttribute(RequestId.ATTRIBUTE_NAME)).isEqualTo(requestId);
  }

  @Test
  void reusesValidRequestIdHeader() throws Exception {
    String incomingRequestId = "client-request_123.abc";
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/questions");
    request.addHeader(RequestId.HEADER_NAME, incomingRequestId);
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, (servletRequest, servletResponse) -> {
    });

    assertThat(response.getHeader(RequestId.HEADER_NAME)).isEqualTo(incomingRequestId);
    assertThat(request.getAttribute(RequestId.ATTRIBUTE_NAME)).isEqualTo(incomingRequestId);
  }

  @Test
  void replacesInvalidRequestIdHeader() throws Exception {
    String invalidRequestId = "bad\nrequest-id";
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/questions");
    request.addHeader(RequestId.HEADER_NAME, invalidRequestId);
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, (servletRequest, servletResponse) -> {
    });

    String requestId = response.getHeader(RequestId.HEADER_NAME);

    assertThat(requestId).isNotEqualTo(invalidRequestId);
    assertThat(RequestId.isValid(requestId)).isTrue();
    assertThat(request.getAttribute(RequestId.ATTRIBUTE_NAME)).isEqualTo(requestId);
  }

  @Test
  void keepsRequestIdHeaderWhenRequestEndsWithBadRequest() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/questions");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, (servletRequest, servletResponse) -> {
      ((HttpServletResponse) servletResponse).setStatus(HttpServletResponse.SC_BAD_REQUEST);
    });

    assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
    assertThat(response.getHeader(RequestId.HEADER_NAME)).isNotBlank();
    assertThat(RequestId.isValid(response.getHeader(RequestId.HEADER_NAME))).isTrue();
  }
}
