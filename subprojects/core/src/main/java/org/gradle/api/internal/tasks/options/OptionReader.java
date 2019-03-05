/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.tasks.options;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.gradle.api.Transformer;
import org.gradle.api.internal.GeneratedSubclasses;
import org.gradle.api.tasks.options.Option;
import org.gradle.api.tasks.options.OptionValues;
import org.gradle.cache.internal.CrossBuildInMemoryCache;
import org.gradle.cache.internal.CrossBuildInMemoryCacheFactory;
import org.gradle.internal.reflect.ClassDetails;
import org.gradle.internal.reflect.ClassInspector;
import org.gradle.internal.reflect.JavaMethod;
import org.gradle.util.CollectionUtils;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class OptionReader {
    private final OptionValueNotationParserFactory optionValueNotationParserFactory = new OptionValueNotationParserFactory();
    private final ClassInspector classInspector;
    private final CrossBuildInMemoryCache<Class<?>, ClassOptionDetails> cachedOptions;

    public OptionReader(ClassInspector classInspector, CrossBuildInMemoryCacheFactory cacheFactory) {
        this.classInspector = classInspector;
        this.cachedOptions = cacheFactory.newClassCache();
    }

    public List<OptionDescriptor> getOptions(Object target) {
        final Class<?> targetClass = GeneratedSubclasses.unpackType(target);
        Map<String, OptionDescriptor> options = new HashMap<String, OptionDescriptor>();
        ClassOptionDetails optionsDetails = cachedOptions.get(targetClass, new Transformer<ClassOptionDetails, Class<?>>() {
            @Override
            public ClassOptionDetails transform(Class<?> aClass) {
                return loadClassDescriptorInCache(targetClass);
            }
        });
        for (OptionElement optionElement : optionsDetails.options) {
            JavaMethod<Object, Collection> optionValueMethod = optionsDetails.optionMethods.get(optionElement);
            options.put(optionElement.getOptionName(), new InstanceOptionDescriptor(target, optionElement, optionValueMethod));
        }
        return CollectionUtils.sort(options.values());
    }

    private ClassOptionDetails loadClassDescriptorInCache(Class<?> targetClass) {
        ClassDetails classDetails = classInspector.inspect(targetClass);
        ImmutableList<OptionElement> optionElements = getOptionElements(classDetails);
        List<JavaMethod<Object, Collection>> optionValueMethods = loadValueMethodForOption(targetClass, classDetails);
        Set<String> processedOptionElements = new HashSet<String>();
        ImmutableMap.Builder<OptionElement, JavaMethod<Object, Collection>> methodsBuilder = ImmutableMap.builderWithExpectedSize(optionElements.size());
        for (OptionElement optionElement : optionElements) {
            if (processedOptionElements.contains(optionElement.getOptionName())) {
                throw new OptionValidationException(String.format("@Option '%s' linked to multiple elements in class '%s'.",
                    optionElement.getOptionName(), targetClass.getName()));
            }
            processedOptionElements.add(optionElement.getOptionName());
            JavaMethod<Object, Collection> optionValueMethodForOption = getOptionValueMethodForOption(optionValueMethods, optionElement);
            if (optionValueMethodForOption != null) {
                methodsBuilder.put(optionElement, optionValueMethodForOption);
            }
        }

        return new ClassOptionDetails(optionElements, methodsBuilder.build());
    }

    @Nullable
    private static JavaMethod<Object, Collection> getOptionValueMethodForOption(List<JavaMethod<Object, Collection>> optionValueMethods, OptionElement optionElement) {
        JavaMethod<Object, Collection> valueMethod = null;
        for (JavaMethod<Object, Collection> optionValueMethod : optionValueMethods) {
            String[] optionNames = getOptionNames(optionValueMethod);
            if (CollectionUtils.toList(optionNames).contains(optionElement.getOptionName())) {
                if (valueMethod == null) {
                    valueMethod = optionValueMethod;
                } else {
                    throw new OptionValidationException(
                        String.format("@OptionValues for '%s' cannot be attached to multiple methods in class '%s'.",
                            optionElement.getOptionName(),
                            optionValueMethod.getMethod().getDeclaringClass().getName()));
                }
            }
        }
        return valueMethod;
    }

    private static String[] getOptionNames(JavaMethod<Object, Collection> optionValueMethod) {
        OptionValues optionValues = optionValueMethod.getMethod().getAnnotation(OptionValues.class);
        return optionValues.value();
    }

    private ImmutableList<OptionElement> getOptionElements(ClassDetails classDetails) {
        ImmutableList.Builder<OptionElement> allOptionElements = ImmutableList.builder();
        allOptionElements.addAll(getMethodAnnotations(classDetails));
        allOptionElements.addAll(getFieldAnnotations(classDetails));
        return allOptionElements.build();
    }

    private List<OptionElement> getFieldAnnotations(ClassDetails classDetails) {
        List<OptionElement> fieldOptionElements = new ArrayList<OptionElement>();
        for (Field field : classDetails.getAllFields()) {
            Option option = findOption(field);
            if (option != null) {
                fieldOptionElements.add(FieldOptionElement.create(option, field, optionValueNotationParserFactory));
            }
        }
        return fieldOptionElements;
    }

    private Option findOption(Field field) {
        Option option = field.getAnnotation(Option.class);
        if (option != null) {
            if (Modifier.isStatic(field.getModifiers())) {
                throw new OptionValidationException(String.format("@Option on static field '%s' not supported in class '%s'.",
                    field.getName(), field.getDeclaringClass().getName()));
            }
        }
        return option;
    }

    private List<OptionElement> getMethodAnnotations(ClassDetails classDetails) {
        List<OptionElement> methodOptionElements = new ArrayList<OptionElement>();
        for (Method method : classDetails.getAllMethods()) {
            Option option = findOption(method);
            if (option != null) {
                OptionElement methodOptionDescriptor = MethodOptionElement.create(option, method,
                    optionValueNotationParserFactory);
                methodOptionElements.add(methodOptionDescriptor);
            }
        }
        return methodOptionElements;
    }

    private Option findOption(Method method) {
        Option option = method.getAnnotation(Option.class);
        if (option != null) {
            if (Modifier.isStatic(method.getModifiers())) {
                throw new OptionValidationException(String.format("@Option on static method '%s' not supported in class '%s'.",
                    method.getName(), method.getDeclaringClass().getName()));
            }
        }
        return option;
    }

    private static List<JavaMethod<Object, Collection>> loadValueMethodForOption(Class<?> targetClass, ClassDetails classDetails) {
        List<JavaMethod<Object, Collection>> methods = new ArrayList<JavaMethod<Object, Collection>>();
        for (Method method : classDetails.getAllMethods()) {
            JavaMethod<Object, Collection> optionValuesMethod = getAsOptionValuesMethod(targetClass, method);
            if (optionValuesMethod != null) {
                methods.add(optionValuesMethod);
            }
        }
        return methods;
    }

    private static JavaMethod<Object, Collection> getAsOptionValuesMethod(Class<?> type, Method method) {
        OptionValues optionValues = method.getAnnotation(OptionValues.class);
        if (optionValues == null) {
            return null;
        }
        if (Collection.class.isAssignableFrom(method.getReturnType())
            && method.getParameterTypes().length == 0
            && !Modifier.isStatic(method.getModifiers())) {
            return JavaMethod.of(Collection.class, method);
        } else {
            throw new OptionValidationException(
                String.format("@OptionValues annotation not supported on method '%s' in class '%s'. Supported method must be non-static, return a Collection<String> and take no parameters.",
                    method.getName(),
                    type.getName()));
        }
    }

    private static class ClassOptionDetails {
        final ImmutableList<OptionElement> options;
        final ImmutableMap<OptionElement, JavaMethod<Object, Collection>> optionMethods;

        public ClassOptionDetails(ImmutableList<OptionElement> options, ImmutableMap<OptionElement, JavaMethod<Object, Collection>> optionMethods) {
            this.options = options;
            this.optionMethods = optionMethods;
        }
    }
}
