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

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.env.PropertySources;
import org.springframework.validation.annotation.Validated;

/**
 * {@link BeanPostProcessor} to bind {@link PropertySources} to beans annotated with
 * {@link ConfigurationProperties}.
 *
 * 它是一个 bean 的后置处理器，用来绑定属性源给 @ConfigurationProperties 注解的类。
 *
 *
 * @author Dave Syer
 * @author Phillip Webb
 * @author Christian Dupuis
 * @author Stephane Nicoll
 * @author Madhura Bhave
 */
public class ConfigurationPropertiesBindingPostProcessor implements BeanPostProcessor,
		PriorityOrdered, ApplicationContextAware, InitializingBean {

	/**
	 * The bean name that this post-processor is registered with.
	 */
	public static final String BEAN_NAME = ConfigurationPropertiesBindingPostProcessor.class
			.getName();

	/**
	 * The bean name of the configuration properties validator.
	 */
	public static final String VALIDATOR_BEAN_NAME = "configurationPropertiesValidator";

	private ConfigurationBeanFactoryMetadata beanFactoryMetadata;

	private ApplicationContext applicationContext;

	private ConfigurationPropertiesBinder configurationPropertiesBinder;

	@Override
	public void setApplicationContext(ApplicationContext applicationContext)
			throws BeansException {
		this.applicationContext = applicationContext;
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		// We can't use constructor injection of the application context because
		// it causes eager factory bean initialization
		// 我们不能使用应用程序 context 的构造函数注入，因为这会导致初始化工厂bean
		// 先到 IOC 容器里获取 ConfigurationBeanFactoryMetadata 的 bean,这里存储中所有的工厂 bean。
		this.beanFactoryMetadata = this.applicationContext.getBean(
				ConfigurationBeanFactoryMetadata.BEAN_NAME,
				ConfigurationBeanFactoryMetadata.class);
		// 初始化一个绑定器设置好配置属性名的校验器
		this.configurationPropertiesBinder = new ConfigurationPropertiesBinder(
				this.applicationContext, VALIDATOR_BEAN_NAME);
	}

	@Override
	public int getOrder() {
		return Ordered.HIGHEST_PRECEDENCE + 1;
	}

	// 在所有的 bean 初始化之前先判断当前 bean 有没有 @ConfigurationProperties 注解，如果有的话则从配置源里找到相应的属性对它进行属性设置
	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName)
			throws BeansException {
		// 判断 bean 里是否带有 @ConfigurationProperties 注解
		ConfigurationProperties annotation = getAnnotation(bean, beanName,
				ConfigurationProperties.class);
		if (annotation != null) {
			// 对当前的 bean 绑属性 重点！！！
			bind(bean, beanName, annotation);
		}
		return bean;
	}
	// 属性绑定
	private void bind(Object bean, String beanName, ConfigurationProperties annotation) {
		// 获取 bean 的 type
		ResolvableType type = getBeanType(bean, beanName);
		// 查找这个 bean 上有没有 @Validated 注解
		Validated validated = getAnnotation(bean, beanName, Validated.class);
		// 把这个 bean 上的 @ConfigurationProperties 注解 和 @Validated（如果有的话）放入一个 Annotation 数组
		Annotation[] annotations = (validated != null)
				? new Annotation[] { annotation, validated }
				: new Annotation[] { annotation };
		// 创建一个 Bindable 对象，里面包含被绑定类的所有属性（value）、注解和类型
		Bindable<?> target = Bindable.of(type).withExistingValue(bean)
				.withAnnotations(annotations);
		try {
			// 开始绑定
			this.configurationPropertiesBinder.bind(target);
		}
		catch (Exception ex) {
			throw new ConfigurationPropertiesBindException(beanName, bean, annotation,
					ex);
		}
	}
	// 看下 bean 是不是有工厂方法 如果没有则判断为普通配置类
	private ResolvableType getBeanType(Object bean, String beanName) {
		Method factoryMethod = this.beanFactoryMetadata.findFactoryMethod(beanName);
		if (factoryMethod != null) {
			return ResolvableType.forMethodReturnType(factoryMethod);
		}
		return ResolvableType.forClass(bean.getClass());
	}

	private <A extends Annotation> A getAnnotation(Object bean, String beanName,
			Class<A> type) {
		A annotation = this.beanFactoryMetadata.findFactoryAnnotation(beanName, type);
		if (annotation == null) {
			annotation = AnnotationUtils.findAnnotation(bean.getClass(), type);
		}
		return annotation;
	}

}
