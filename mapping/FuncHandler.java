package com.godmao.func.mapping;

import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import javax.annotation.PostConstruct;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.Function;

public abstract class FuncHandler<Controller extends Annotation, Mapping extends Annotation> {
    private SFunction<Mapping, String[]> mappikey = null;
    //    private List<FuncMethod> funcMethods = null;//
    private Map<String, FuncMethod> funcMethods = null;//

    @FunctionalInterface
    public interface SFunction<T, R> extends Function<T, R>, Serializable {

    }

    @Data
    private static class FuncMethod<Bean/*, ResultType*/> {
        private String key;
        private Bean beanObj;

        private Method method;
        private Boolean hasResult;
        //        private Parameter[] parameters;
        private Map<String, Class<?>> parameters;
    }

    @Data
    public static class FuncResult<Result> {
        private Boolean hasResult;
        private Result result;
    }

    @Autowired
    private ApplicationContext applicationContext;

    @PostConstruct
    public void init() {

        Type genericSuperclass = this.getClass().getGenericSuperclass();
        ParameterizedType parameterizedType = (ParameterizedType) genericSuperclass;//
        Class<Controller> controllerClass = (Class<Controller>) parameterizedType.getActualTypeArguments()[0];
        Class<Mapping> mappingClass = (Class<Mapping>) parameterizedType.getActualTypeArguments()[1];
        this.mappikey = setMappikey();
//        this.funcMethods = new ArrayList<>();
        this.funcMethods = new HashMap<>();

        Map<String, Object> beansWithAnnotation = applicationContext.getBeansWithAnnotation(controllerClass);

        for (Object beanObj : beansWithAnnotation.values()) {
            Method[] methods = beanObj.getClass().getMethods();
            for (Method method : methods) {
                Mapping mapping = method.getAnnotation(mappingClass);
                if (null == mapping) {
                    continue;
                }
                String[] keys = this.mappikey.apply(mapping);
                for (String key : keys) {

                    FuncMethod funcMethod = new FuncMethod();
                    funcMethod.setKey(key);
                    funcMethod.setMethod(method);
                    funcMethod.setBeanObj(beanObj);
                    funcMethod.setHasResult(!method.getReturnType().isAssignableFrom(void.class));
                    funcMethod.setParameters(new LinkedHashMap<>(method.getParameters().length));

                    for (Parameter parameter : method.getParameters()) {
                        String paramename = parameter.getName();
                        Class<?> parametype = parameter.getType();
                        funcMethod.getParameters().put(paramename, parametype);
                    }

                    this.funcMethods.put(key, funcMethod);
                }
            }
        }
        System.out.println("==================================");
    }

    public Object[] convertArgs(String key, Object obj) {
        Object[] result;
        if (obj instanceof JSONObject) {
            result = convertArgs(key, (JSONObject) obj);
        } else if (obj instanceof Map) {
            result = convertArgs(key, new JSONObject((Map) obj));
        } else {
            result = convertArgs(key, JSONObject.parseObject(JSONObject.toJSONString(obj)));
//            result = convertArgs(key, null);
        }
        return result;
    }


    public Object[] convertArgs(String key, JSONObject body) {
        FuncMethod funcMethod = this.funcMethods.get(key);
        Map<String, Class<?>> parameters = funcMethod.getParameters();
        Object[] result = new Object[parameters.size()];
        Set<String> paramenames = parameters.keySet();

        if (paramenames.size() == 0) {

        } else if (paramenames.size() == 1) {
            for (String paramename : paramenames) {
                Class<?> parametype = parameters.get(paramename);
                Object obj;
                if (body.containsKey(paramename)) {
                    obj = body.getObject(paramename, parametype);
                } else {
                    //
//                    obj = body.toJavaObject(parametype);
                    {
                        try {
                            obj = body.toJavaObject(parametype);
                        } catch (JSONException e) {
                            obj = null;
                            boolean isAssignable = false;
                            for (String k : body.keySet()) {
                                if (parametype.isAssignableFrom(body.get(k).getClass())) {
                                    isAssignable = true;
                                    obj = body.getObject(k, parametype);
                                    break;
                                }
                            }
                            if (!isAssignable) {
                                obj = body.getObject(paramename, parametype);
                            }
                        }
                    }

                }
                result[0] = obj;
            }
        } else {
            int index = 0;
            for (String paramename : paramenames) {
                Class<?> parametype = parameters.get(paramename);


                {
                    Object obj = null;
                    if (body.containsKey(paramename)) {
                        obj = body.getObject(paramename, parametype);
                        body.remove(paramename);
                    } else {
                        boolean isAssignable = false;
                        for (String k : body.keySet()) {
                            if (parametype.isAssignableFrom(body.get(k).getClass())) {
                                isAssignable = true;
                                obj = body.getObject(k, parametype);
                                break;
                            }
                        }
                        if (!isAssignable) {
                            obj = body.getObject(paramename, parametype);
                        }
                    }
                    result[index] = obj;
                    index++;
                }


                //
//                Object obj = body.getObject(paramename, parametype);
//                result[index] = obj;
//                index++;
            }
        }

        return result;
    }


    public <R> FuncResult<R> invoke(String key, Object... valus)
            throws IllegalAccessException,
            IllegalArgumentException,
            InvocationTargetException {
        FuncResult<R> funcResult = new FuncResult<>();
        FuncMethod funcMethod = this.funcMethods.get(key);
        Object beanObj = funcMethod.getBeanObj();
        Method method = funcMethod.getMethod();
        Boolean hasResult = funcMethod.getHasResult();
        funcResult.setHasResult(hasResult);
        funcResult.setResult((R) method.invoke(beanObj, valus));
        return funcResult;
    }

    abstract SFunction<Mapping, String[]> setMappikey();

}


