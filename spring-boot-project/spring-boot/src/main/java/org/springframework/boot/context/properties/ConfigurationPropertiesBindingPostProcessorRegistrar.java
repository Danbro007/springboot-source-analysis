/*
 * Copyright 2012-2018 the original author or authors.
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

package org.springframework.boot.context.properties;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;

/**
 * {@link ImportBeanDefinitionRegistrar} for binding externalized application properties
 * to {@link ConfigurationProperties} beans.
 *
 * 把配置文件里的相关属性值绑定给 @ConfigurationProperties 注解的 bean
 *
 * @author Dave Syer
 * @author Phillip Webb
 */
public class ConfigurationPropertiesBindingPostProcessorRegistrar
		implements ImportBeanDefinitionRegistrar {
	// 把 ConfigurationPropertiesBindingPostProcessor 和 ConfigurationBeanFactoryMetadata 注册进 IOC 容器
	@Override
	public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata,
			BeanDefinitionRegistry registry) {
		// 如果 IOC 容器里没有 ConfigurationPropertiesBindingPostProcessor 这个 bean 则把它 和 ConfigurationBeanFactoryMetadata 则先注册进 IOC 容器里
		if (!registry.containsBeanDefinition(
				ConfigurationPropertiesBindingPostProcessor.BEAN_NAME)) {
			// ConfigurationPropertiesBindingPostProcessor 是 Bean后置处理器,用来绑定配置文件里的属性给
			// @ConfigurationProperties 注解的类
			registerConfigurationPropertiesBindingPostProcessor(registry);
			// ConfigurationBeanFactoryMetadata 是 BeanFactory后置处理器
			registerConfigurationBeanFactoryMetadata(registry);
		}
	}

	private void registerConfigurationPropertiesBindingPostProcessor(
			BeanDefinitionRegistry registry) {
		GenericBeanDefinition definition = new GenericBeanDefinition();
		definition.setBeanClass(ConfigurationPropertiesBindingPostProcessor.class);
		definition.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
		registry.registerBeanDefinition(
				ConfigurationPropertiesBindingPostProcessor.BEAN_NAME, definition);

	}

	private void registerConfigurationBeanFactoryMetadata(
			BeanDefinitionRegistry registry) {
		GenericBeanDefinition definition = new GenericBeanDefinition();
		definition.setBeanClass(ConfigurationBeanFactoryMetadata.class);
		definition.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
		registry.registerBeanDefinition(ConfigurationBeanFactoryMetadata.BEAN_NAME,
				definition);
	}

}
