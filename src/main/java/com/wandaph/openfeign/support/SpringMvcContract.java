package com.wandaph.openfeign.support;

import com.wandaph.openfeign.parameters.AnnotatedParameterProcessor;
import com.wandaph.openfeign.parameters.PathVariableParameterProcessor;
import com.wandaph.openfeign.parameters.RequestHeaderParameterProcessor;
import com.wandaph.openfeign.parameters.RequestParamParameterProcessor;
import feign.Contract;
import feign.Feign;
import feign.MethodMetadata;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static feign.Util.checkState;
import static feign.Util.emptyToNull;

/**
 * FileName: SpringMvcContract
 * Author:   lvzhen
 * Date:      2019/3/3
 * Description: SpringMvc 常用注解处理
 */
public class SpringMvcContract extends Contract.BaseContract
        implements ResourceLoaderAware {

    private static final String ACCEPT = "Accept";

    private static final String CONTENT_TYPE = "Content-Type";

    private final Map<Class<? extends Annotation>, AnnotatedParameterProcessor> annotatedArgumentProcessors;
    private final Map<String, Method> processedMethods = new HashMap();

    private final ConversionService conversionService;

    private ResourceLoader resourceLoader = new DefaultResourceLoader();

    public SpringMvcContract() {
        this(Collections.<AnnotatedParameterProcessor>emptyList());
    }

    public SpringMvcContract(
            List<AnnotatedParameterProcessor> annotatedParameterProcessors) {
        this(annotatedParameterProcessors, new DefaultConversionService());
    }

    public SpringMvcContract(
            List<AnnotatedParameterProcessor> annotatedParameterProcessors,
            ConversionService conversionService) {
        Assert.notNull(annotatedParameterProcessors,
                "Parameter processors can not be null.");
        Assert.notNull(conversionService, "ConversionService can not be null.");
        List<AnnotatedParameterProcessor> processors;
        if (!annotatedParameterProcessors.isEmpty()) {
            processors = new ArrayList(annotatedParameterProcessors);
        } else {
            processors = getDefaultAnnotatedArgumentsProcessors();
        }
        this.annotatedArgumentProcessors = toAnnotatedArgumentProcessorMap(processors);
        this.conversionService = conversionService;
    }

    /**
     * 构建默认参数处理器
     *
     * @return list
     */
    private List<AnnotatedParameterProcessor> getDefaultAnnotatedArgumentsProcessors() {
        List<AnnotatedParameterProcessor> annotatedArgumentResolvers = new ArrayList();
        annotatedArgumentResolvers.add(new PathVariableParameterProcessor());
        annotatedArgumentResolvers.add(new RequestParamParameterProcessor());
        annotatedArgumentResolvers.add(new RequestHeaderParameterProcessor());
        return annotatedArgumentResolvers;
    }

    /**
     * 转换Map对象 Map<Class<? extends Annotation>
     *
     * @param processors list集合
     * @return map
     */
    private Map<Class<? extends Annotation>, AnnotatedParameterProcessor> toAnnotatedArgumentProcessorMap(
            List<AnnotatedParameterProcessor> processors) {
        Map<Class<? extends Annotation>, AnnotatedParameterProcessor> result = new HashMap();
        for (AnnotatedParameterProcessor processor : processors) {
            result.put(processor.getAnnotationType(), processor);
        }
        return result;
    }

    /**
     * 解析检验元数据
     *
     * @param targetType
     * @param method
     * @return
     */
    @Override
    public MethodMetadata parseAndValidateMetadata(Class<?> targetType, Method method) {
        this.processedMethods.put(Feign.configKey(targetType, method), method);
        MethodMetadata md = super.parseAndValidateMetadata(targetType, method);
        RequestMapping classAnnotation = method.getAnnotation(RequestMapping.class);
        if (classAnnotation != null) {
            // produces - use from class annotation only if method has not specified this
            if (!md.template().headers().containsKey(ACCEPT)) {
                parseProduces(md, method, classAnnotation);
            }
            // consumes -- use from class annotation only if method has not specified this
            if (!md.template().headers().containsKey(CONTENT_TYPE)) {
                parseConsumes(md, method, classAnnotation);
            }
            // headers -- class annotation is inherited to methods, always write these if
            // present
            parseHeaders(md, method, classAnnotation);
        }
        return md;
    }

    /**
     * 基于类的注解
     *
     * @param data
     * @param clz
     */
    @Override
    protected void processAnnotationOnClass(MethodMetadata data, Class<?> clz) {
        if (clz.isAnnotationPresent(RequestMapping.class)) {
            RequestMapping classAnnotation = clz.getAnnotation(RequestMapping.class);
            if (classAnnotation != null) {
                // Prepend path from class annotation if specified
                if (classAnnotation.value().length > 0) {
                    String pathValue = emptyToNull(classAnnotation.value()[0]);
                    pathValue = resolve(pathValue);
                    if (!pathValue.startsWith("/")) {
                        pathValue = "/" + pathValue;
                    }
                    data.template().insert(0, pathValue);
                }
            }
        }
    }


    /**
     * 基于方法的注解
     *
     * @param data
     * @param annotation
     * @param method
     */
    @Override
    protected void processAnnotationOnMethod(MethodMetadata data, Annotation annotation, Method method) {
        if (!RequestMapping.class.isInstance(annotation) && !annotation
                .annotationType().isAnnotationPresent(RequestMapping.class)) {
            return;
        }
        RequestMapping methodMapping = method.getAnnotation(RequestMapping.class);
        // HTTP Method
        RequestMethod[] methods = methodMapping.method();
        if (methods.length == 0) {
            methods = new RequestMethod[]{RequestMethod.GET};
        }
        checkOne(method, methods, "method");
        data.template().method(methods[0].name());
        // path
        checkAtMostOne(method, methodMapping.value(), "value");
        if (methodMapping.value().length > 0) {
            String pathValue = emptyToNull(methodMapping.value()[0]);
            if (pathValue != null) {
                pathValue = resolve(pathValue);
                // Append path from @RequestMapping if value is present on method
                if (!pathValue.startsWith("/")
                        && !data.template().toString().endsWith("/")) {
                    pathValue = "/" + pathValue;
                }
                data.template().append(pathValue);
            }
        }
        // produces  Accept  "application/json"
        parseProduces(data, method, methodMapping);
        // consumes  Content-Type  application/json
        parseConsumes(data, method, methodMapping);
        // headers
        parseHeaders(data, method, methodMapping);
    }

    /**
     * 基于参数的注解
     *
     * @param data
     * @param annotations
     * @param paramIndex
     * @return
     */
    @Override
    protected boolean processAnnotationsOnParameter(MethodMetadata data, Annotation[] annotations, int paramIndex) {
        boolean isHttpAnnotation = false;
        AnnotatedParameterProcessor.AnnotatedParameterContext context = new SimpleAnnotatedParameterContext(
                data, paramIndex);
        Method method = this.processedMethods.get(data.configKey());
        for (Annotation parameterAnnotation : annotations) {
            AnnotatedParameterProcessor processor = this.annotatedArgumentProcessors
                    .get(parameterAnnotation.annotationType());
            if (processor != null) {
                Annotation processParameterAnnotation;
                isHttpAnnotation |= processor.processArgument(context,
                        parameterAnnotation, method);
            }
        }
        return isHttpAnnotation;
    }

    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }


    private void parseProduces(MethodMetadata md, Method method,
                               RequestMapping annotation) {
        String[] serverProduces = annotation.produces();
        //如果null设置 Accept  "application/json"
        String clientAccepts = serverProduces.length == 0 ? "application/json"
                : emptyToNull(serverProduces[0]);
        if (clientAccepts != null) {
            md.template().header(ACCEPT, clientAccepts);
        }
    }

    private void parseConsumes(MethodMetadata md, Method method,
                               RequestMapping annotation) {
        String[] serverConsumes = annotation.consumes();
        //如果null设置 Content-Type  application/json
        String clientProduces = serverConsumes.length == 0 ? "application/json"
                : emptyToNull(serverConsumes[0]);
        if (clientProduces != null) {
            md.template().header(CONTENT_TYPE, clientProduces);
        }
    }

    private void parseHeaders(MethodMetadata md, Method method,
                              RequestMapping annotation) {
        // TODO: only supports one header value per key
        if (annotation.headers() != null && annotation.headers().length > 0) {
            for (String header : annotation.headers()) {
                int index = header.indexOf('=');
                if (!header.contains("!=") && index >= 0) {
                    md.template().header(resolve(header.substring(0, index)),
                            resolve(header.substring(index + 1).trim()));
                }
            }
        }
    }

    private String resolve(String value) {
        if (StringUtils.hasText(value)
                && this.resourceLoader instanceof ConfigurableApplicationContext) {
            return ((ConfigurableApplicationContext) this.resourceLoader).getEnvironment()
                    .resolvePlaceholders(value);
        }
        return value;
    }


    private void checkOne(Method method, Object[] values, String fieldName) {
        checkState(values != null && values.length == 1,
                "Method %s can only contain 1 %s field. Found: %s", method.getName(),
                fieldName, values == null ? null : Arrays.asList(values));
    }

    private void checkAtMostOne(Method method, Object[] values, String fieldName) {
        checkState(values != null && (values.length == 0 || values.length == 1),
                "Method %s can only contain at most 1 %s field. Found: %s",
                method.getName(), fieldName,
                values == null ? null : Arrays.asList(values));
    }

    private class SimpleAnnotatedParameterContext
            implements AnnotatedParameterProcessor.AnnotatedParameterContext {
        private final MethodMetadata methodMetadata;
        private final int parameterIndex;

        public SimpleAnnotatedParameterContext(MethodMetadata methodMetadata, int parameterIndex) {
            this.methodMetadata = methodMetadata;
            this.parameterIndex = parameterIndex;
        }

        @Override
        public MethodMetadata getMethodMetadata() {
            return this.methodMetadata;
        }

        @Override
        public int getParameterIndex() {
            return this.parameterIndex;
        }

        @Override
        public void setParameterName(String name) {
            nameParam(this.methodMetadata, name, this.parameterIndex);
        }

        @Override
        public Collection<String> setTemplateParameter(String name, Collection<String> rest) {
            return addTemplatedParam(rest, name);
        }
    }

}
