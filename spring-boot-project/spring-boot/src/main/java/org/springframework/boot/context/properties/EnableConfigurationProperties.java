/*
 * Copyright 2012-2017 the original author or authors.
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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * Enable support for {@link ConfigurationProperties} annotated beans.
 *
 * 开启对 @ConfigurationProperties 注解的 bean 的支持
 *
 * {@link ConfigurationProperties} beans can be registered in the standard way (for
 * example using {@link Bean @Bean} methods) or, for convenience, can be specified
 * directly on this annotation.
 *
 * @ConfigurationProperties 注解的 bean 能在标准方式（比如使用过 @Bean 方法）里被注册，
 * 或者为了方便直接通过 @ConfigurationProperties 注解来指定。
 *
 * @author Dave Syer
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(EnableConfigurationPropertiesImportSelector.class)
public @interface EnableConfigurationProperties {

	/**
	 * Convenient way to quickly register {@link ConfigurationProperties} annotated beans
	 * with Spring. Standard Spring Beans will also be scanned regardless of this value.
	 *
	 * 快速注册 @ConfigurationProperties 注解的 bean 的便捷方式。无论这个值是多少，标准的Spring bean都将被扫描。
	 *
	 * @return {@link ConfigurationProperties} annotated beans to register
	 */
	Class<?>[] value() default {};

}
