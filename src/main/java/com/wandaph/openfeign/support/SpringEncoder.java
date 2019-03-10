package com.wandaph.openfeign.support;

import feign.RequestTemplate;
import feign.codec.EncodeException;
import feign.codec.Encoder;
import feign.form.spring.SpringFormEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.BufferedImageHttpMessageConverter;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import static com.wandaph.openfeign.support.FeignUtils.getHeaders;
import static com.wandaph.openfeign.support.FeignUtils.getHttpHeaders;

/**
 * FileName: SpringEncoder
 * Author:   lvzhen
 * Date:      2019/3/3
 * Description: Spring 编码器
 */
public class SpringEncoder implements Encoder {

    public Logger log = LoggerFactory.getLogger(SpringEncoder.class);

    private final SpringFormEncoder springFormEncoder = new SpringFormEncoder();

    private List<HttpMessageConverter<?>> messageConverters;

    public SpringEncoder() {
        messageConverters = new ArrayList();
        messageConverters.add(new StringHttpMessageConverter());
        messageConverters.add(new ByteArrayHttpMessageConverter());
        messageConverters.add(new BufferedImageHttpMessageConverter());
        messageConverters.add(new MappingJackson2HttpMessageConverter());
        //messageConverters.add(new FastJsonHttpMessageConverter());
    }

    @Override
    public void encode(Object requestBody, Type bodyType, RequestTemplate request)
            throws EncodeException {
        // template.body(conversionService.convert(object, String.class));
        if (requestBody != null) {
            Class<?> requestType = requestBody.getClass();
            Collection<String> contentTypes = request.headers().get("Content-Type");

            MediaType requestContentType = null;
            if (contentTypes != null && !contentTypes.isEmpty()) {
                String type = contentTypes.iterator().next();
                requestContentType = MediaType.valueOf(type);
            }

            if (bodyType != null && bodyType.equals(MultipartFile.class)) {
                if (Objects.equals(requestContentType, MediaType.MULTIPART_FORM_DATA)) {
                    springFormEncoder.encode(requestBody, bodyType, request);
                    return;
                } else {
                    String message = "Content-Type \"" + MediaType.MULTIPART_FORM_DATA +
                            "\" not set for request body of type " +
                            requestBody.getClass().getSimpleName();
                    throw new EncodeException(message);
                }
            }

            for (HttpMessageConverter<?> messageConverter : messageConverters) {
                if (messageConverter.canWrite(requestType, requestContentType)) {
                    if (log.isDebugEnabled()) {
                        if (requestContentType != null) {
                            log.debug("Writing [" + requestBody + "] as \""
                                    + requestContentType + "\" using ["
                                    + messageConverter + "]");
                        } else {
                            log.debug("Writing [" + requestBody + "] using ["
                                    + messageConverter + "]");
                        }

                    }

                    FeignOutputMessage outputMessage = new FeignOutputMessage(request);
                    try {
                        @SuppressWarnings("unchecked")
                        HttpMessageConverter<Object> copy = (HttpMessageConverter<Object>) messageConverter;
                        copy.write(requestBody, requestContentType, outputMessage);
                    } catch (IOException ex) {
                        throw new EncodeException("Error converting request body", ex);
                    }
                    // clear headers
                    request.headers(null);
                    // converters can modify headers, so update the request
                    // with the modified headers
                    request.headers(getHeaders(outputMessage.getHeaders()));

                    // do not use charset for binary data and protobuf
                    Charset charset;
                    if (messageConverter instanceof ByteArrayHttpMessageConverter) {
                        charset = null;
                    }/* else if (messageConverter instanceof ProtobufHttpMessageConverter &&
                            ProtobufHttpMessageConverter.PROTOBUF.isCompatibleWith(outputMessage.getHeaders().getContentType())) {
                        charset = null;
                    }*/ else {
                        charset = StandardCharsets.UTF_8;
                    }
                    request.body(outputMessage.getOutputStream().toByteArray(), charset);
                    return;
                }
            }
            String message = "Could not write request: no suitable HttpMessageConverter "
                    + "found for request type [" + requestType.getName() + "]";
            if (requestContentType != null) {
                message += " and content type [" + requestContentType + "]";
            }
            throw new EncodeException(message);
        }
    }

    private class FeignOutputMessage implements HttpOutputMessage {

        private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        private final HttpHeaders httpHeaders;

        private FeignOutputMessage(RequestTemplate request) {
            httpHeaders = getHttpHeaders(request.headers());
        }

        @Override
        public OutputStream getBody() throws IOException {
            return this.outputStream;
        }

        @Override
        public HttpHeaders getHeaders() {
            return this.httpHeaders;
        }

        public ByteArrayOutputStream getOutputStream() {
            return this.outputStream;
        }

    }

}
