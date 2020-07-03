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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.Assert;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

/**
 * Import selector that sets up binding of external properties to configuration classes
 * (see {@link ConfigurationProperties}). It either registers a
 * {@link ConfigurationProperties} bean or not, depending on whether the enclosing
 * {@link EnableConfigurationProperties} explicitly declares one. If none is declared then
 * a bean post processor will still kick in for any beans annotated as external
 * configuration. If one is declared then it a bean definition is registered with id equal
 * to the class name (thus an application context usually only contains one
 * {@link ConfigurationProperties} bean of each unique type).
 *
 * 导入选择器，用于设置外部属性（如配置文件 application.properties）到配置类的绑定(参见{@link ConfigurationProperties})。
 * 它要么注册一个 ConfigurationProperties bean 要么不注册，取决于 @EnableConfigurationProperties 注解是否显式声明。
 * 如果没有声明，那一个 bean 后置处理器仍然会为 注解是外部配置的 bean 提供支持。如果显示声明了，会以 id 为完全限定类名注册一个 BD
 * （因此，应用程序上下文通常只包含每种独特类型的一个 ConfigurationProperties bean）
 *
 * @author Dave Syer
 * @author Christian Dupuis
 * @author Stephane Nicoll
 */
class EnableConfigurationPropertiesImportSelector implements ImportSelector {
	// 【1】注册 ConfigurationPropertiesBeanRegistrar 和 ConfigurationPropertiesBindingPostProcessorRegistrar 到 IOC 容器
	private static final String[] IMPORTS = {
			ConfigurationPropertiesBeanRegistrar.class.getName(),
			ConfigurationPropertiesBindingPostProcessorRegistrar.class.getName() };

	@Override
	public String[] selectImports(AnnotationMetadata metadata) {
		return IMPORTS;
	}

	/**
	 * {@link ImportBeanDefinitionRegistrar} for configuration properties support.
	 *
	 * 它的作用就是把当前类里的 @EnableConfigurationProperties 注解里的类转换 BD 对象存入 BD Map 里
	 * 之后会把这些类注册进 IOC 容器里。
	 *
	 */
	public static class ConfigurationPropertiesBeanRegistrar
			implements ImportBeanDefinitionRegistrar {
		// 【2】把当前自动配置类里 @EnableConfigurationProperties 标注的类注册进入 IOC 容器里
		@Override
		public void registerBeanDefinitions(AnnotationMetadata metadata,
				BeanDefinitionRegistry registry) {
			getTypes(metadata).forEach((type) -> register(registry,
					(ConfigurableListableBeanFactory) registry, type));
		}
		// 【3】获取当前类里所有 @EnableConfigurationProperties 注解的类并转换成 List 返回
		// 例如：@EnableConfigurationProperties(ServerProperties.class) 则获取的是 ServerProperties
		private List<Class<?>> getTypes(AnnotationMetadata metadata) {
			MultiValueMap<String, Object> attributes = metadata
					.getAllAnnotationAttributes(
							EnableConfigurationProperties.class.getName(), false);
			return collectClasses((attributes != null) ? attributes.get("value")
					: Collections.emptyList());
		}

		private List<Class<?>> collectClasses(List<?> values) {
			return values.stream().flatMap((value) -> Arrays.stream((Object[]) value))
					.map((o) -> (Class<?>) o).filter((type) -> void.class != type)
					.collect(Collectors.toList());
		}
		// 以类的完全限定名为key 把
		private void register(BeanDefinitionRegistry registry,
				ConfigurableListableBeanFactory beanFactory, Class<?> type) {
			String name = getName(type);
			// 通过 BeanName 在 BeanFactory 里查看是否有这个 bean，没有的话注册进 BD Map 里
			if (!containsBeanDefinition(beanFactory, name)) {
				registerBeanDefinition(registry, name, type);
			}
		}

		private String getName(Class<?> type) {
			ConfigurationProperties annotation = AnnotationUtils.findAnnotation(type,
					ConfigurationProperties.class);
			String prefix = (annotation != null) ? annotation.prefix() : "";
			return (StringUtils.hasText(prefix) ? prefix + "-" + type.getName()
					: type.getName());
		}

		private boolean containsBeanDefinition(
				ConfigurableListableBeanFactory beanFactory, String name) {
			if (beanFactory.containsBeanDefinition(name)) {
				return true;
			}
			BeanFactory parent = beanFactory.getParentBeanFactory();
			if (parent instanceof ConfigurableListableBeanFactory) {
				return containsBeanDefinition((ConfigurableListableBeanFactory) parent,
						name);
			}
			return false;
		}
		// 为 bean 创建一个 BD 对象 然后注册进 IOC 容器里
		private void registerBeanDefinition(BeanDefinitionRegistry registry, String name,
				Class<?> type) {
			assertHasAnnotation(type);
			GenericBeanDefinition definition = new GenericBeanDefinition();
			definition.setBeanClass(type);
			registry.registerBeanDefinition(name, definition);
		}

		private void assertHasAnnotation(Class<?> type) {
			Assert.notNull(
					AnnotationUtils.findAnnotation(type, ConfigurationProperties.class),
					() -> "No " + ConfigurationProperties.class.getSimpleName()
							+ " annotation found on  '" + type.getName() + "'.");
		}

	}

}
