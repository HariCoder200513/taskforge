package io.taskforge.web;

import io.taskforge.config.AuthProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * Builds {@link RequestMeta} from the servlet request. The client IP is taken
 * from the proxy's forwarded header ONLY when the request carries the correct
 * proxy secret; otherwise the direct remote address is used (the forwarded
 * header is untrusted and spoofable without the secret).
 */
@Component
public class RequestMetaResolver {

    private static final String PROXY_SECRET_HEADER = "X-Proxy-Secret";

    private final AuthProperties props;

    public RequestMetaResolver(AuthProperties props) {
        this.props = props;
    }

    public RequestMeta resolve(HttpServletRequest request) {
        return new RequestMeta(clientIp(request), userAgent(request), requestId(request));
    }

    private String clientIp(HttpServletRequest request) {
        AuthProperties.Proxy proxy = props.proxy();
        if (proxy != null && proxy.secret() != null && !proxy.secret().isBlank()) {
            String presented = request.getHeader(PROXY_SECRET_HEADER);
            if (proxy.secret().equals(presented)) {
                String forwarded = request.getHeader(proxy.clientIpHeader());
                if (forwarded != null && !forwarded.isBlank()) {
                    return forwarded;
                }
            }
        }
        return request.getRemoteAddr();
    }

    private String userAgent(HttpServletRequest request) {
        String ua = request.getHeader("User-Agent");
        if (ua == null) {
            return null;
        }
        return ua.length() > 512 ? ua.substring(0, 512) : ua;
    }

    private String requestId(HttpServletRequest request) {
        Object attr = request.getAttribute(RequestIdFilter.ATTRIBUTE);
        return attr != null ? attr.toString() : null;
    }
}
