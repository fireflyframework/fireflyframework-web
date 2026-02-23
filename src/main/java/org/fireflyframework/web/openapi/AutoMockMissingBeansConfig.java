/*
 * Copyright 2024-2026 Firefly Software Solutions Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.fireflyframework.web.openapi;

import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Automatically registers Mockito mock beans for every {@code @Autowired}
 * dependency of {@code @RestController} classes that is not already present
 * in the bean registry.
 *
 * <p>Mocks are registered as pre-existing singletons via
 * {@link ConfigurableListableBeanFactory#registerSingleton} so that Spring
 * does not attempt to process {@code @Autowired} annotations on the mock
 * objects themselves (which would fail for concrete service classes that
 * have their own unsatisfied dependencies).
 *
 * <p>Activated only under the {@code openapi-gen} Spring profile.
 */
@Configuration
@Profile("openapi-gen")
public class AutoMockMissingBeansConfig implements BeanDefinitionRegistryPostProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AutoMockMissingBeansConfig.class);

    private final Map<String, Class<?>> mocksToRegister = new LinkedHashMap<>();

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        for (String beanName : registry.getBeanDefinitionNames()) {
            BeanDefinition bd = registry.getBeanDefinition(beanName);
            String beanClassName = bd.getBeanClassName();
            if (beanClassName == null) {
                continue;
            }

            Class<?> beanClass;
            try {
                beanClass = Class.forName(beanClassName);
            } catch (ClassNotFoundException e) {
                continue;
            }

            if (!beanClass.isAnnotationPresent(RestController.class)) {
                continue;
            }

            for (Field field : beanClass.getDeclaredFields()) {
                if (!field.isAnnotationPresent(Autowired.class)) {
                    continue;
                }

                Class<?> fieldType = field.getType();
                String mockBeanName = "mock_" + fieldType.getSimpleName();

                if (mocksToRegister.containsKey(fieldType.getName())) {
                    continue;
                }

                if (isBeanTypeRegistered(registry, fieldType)) {
                    continue;
                }

                logger.info("Will register Mockito mock for missing bean: {} (type: {})",
                        mockBeanName, fieldType.getName());
                mocksToRegister.put(fieldType.getName(), fieldType);
            }
        }
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        for (Map.Entry<String, Class<?>> entry : mocksToRegister.entrySet()) {
            Class<?> fieldType = entry.getValue();
            String mockBeanName = "mock_" + fieldType.getSimpleName();

            logger.info("Registering Mockito mock singleton: {} (type: {})",
                    mockBeanName, fieldType.getName());
            beanFactory.registerSingleton(mockBeanName, Mockito.mock(fieldType));
        }
    }

    private boolean isBeanTypeRegistered(BeanDefinitionRegistry registry, Class<?> type) {
        for (String name : registry.getBeanDefinitionNames()) {
            BeanDefinition bd = registry.getBeanDefinition(name);
            String className = bd.getBeanClassName();
            if (className == null) {
                continue;
            }
            try {
                Class<?> clazz = Class.forName(className);
                if (type.isAssignableFrom(clazz)) {
                    return true;
                }
            } catch (ClassNotFoundException e) {
                // Skip
            }
        }
        return false;
    }
}
