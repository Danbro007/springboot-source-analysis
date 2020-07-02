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

package org.springframework.boot.context.properties.bind;

import java.beans.Introspector;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import org.springframework.beans.BeanUtils;
import org.springframework.boot.context.properties.bind.Binder.Context;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.ConfigurationPropertyState;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;

/**
 * {@link BeanBinder} for mutable Java Beans.
 *
 * @author Phillip Webb
 * @author Madhura Bhave
 */
class JavaBeanBinder implements BeanBinder {

	@Override
	public <T> T bind(ConfigurationPropertyName name, Bindable<T> target, Context context,
			BeanPropertyBinder propertyBinder) {
		// 先判断属性存不存在
		boolean hasKnownBindableProperties = hasKnownBindableProperties(name, context);
		// 把 target 转换成 Bean 对象
		Bean<T> bean = Bean.get(target, hasKnownBindableProperties);
		if (bean == null) {
			return null;
		}
		// 从 target 里获取被绑定的类的实例 例如：ServerProperties 的实例
		BeanSupplier<T> beanSupplier = bean.getSupplier(target);
		// 为被绑定类的实例绑定属性，绑定成功返回 true 失败返回 false
		boolean bound = bind(propertyBinder, bean, beanSupplier);
		return (bound ? beanSupplier.get() : null);
	}

	private boolean hasKnownBindableProperties(ConfigurationPropertyName name,
			Context context) {
		for (ConfigurationPropertySource source : context.getSources()) {
			if (source.containsDescendantOf(name) == ConfigurationPropertyState.PRESENT) {
				return true;
			}
		}
		return false;
	}
	// 对 @ConfigurationProperties 注解的类的所有属性进行遍历然后再到配置源里查找属性值，之后使用 setter 方法进行赋值
	private <T> boolean bind(BeanPropertyBinder propertyBinder, Bean<T> bean,
			BeanSupplier<T> beanSupplier) {
		boolean bound = false;
		// 遍历被绑定类的所有属性对属性进行绑定
		for (Map.Entry<String, BeanProperty> entry : bean.getProperties().entrySet()) {
			bound |= bind(beanSupplier, propertyBinder, entry.getValue());
		}
		return bound;
	}
	// 先到找到属性值然后对绑定对象调用属性的 set() 方法绑定属性
	private <T> boolean bind(BeanSupplier<T> beanSupplier,
			BeanPropertyBinder propertyBinder, BeanProperty property) {
		// 获取属性名
		String propertyName = property.getName();
		// 获取属性的类型
		ResolvableType type = property.getType();
		// 用被绑定的类的实例的 get() 方法获取属性的 BeanProperty 对象
		Supplier<Object> value = property.getValue(beanSupplier);
		// 获取属性上的注解
		Annotation[] annotations = property.getAnnotations();
		// 先给属性创建一个 Bindable 对象，给这个对象设置了属性的类型、属性的包装类类型、属性的 BeanProperty 对象和注解，然后到配置源里找属性值，找到后返回。
		Object bound = propertyBinder.bindProperty(propertyName,
				Bindable.of(type).withSuppliedValue(value).withAnnotations(annotations));
		if (bound == null) {
			return false;
		}
		// 如果这个属性可以用 set 方法则把调用 setXX() 方法设置属性
		if (property.isSettable()) {
			// beanSupplier 有被绑定的对象，对这个对象调用属性的 set() 方法设置属性值
			property.setValue(beanSupplier, bound);
		}
		else if (value == null || !bound.equals(value.get())) {
			throw new IllegalStateException(
					"No setter found for property: " + property.getName());
		}
		return true;
	}

	/**
	 * The bean being bound.
	 */
	private static class Bean<T> {
		// 缓存的bean
		private static Bean<?> cached;

		private final Class<?> type;

		private final ResolvableType resolvableType;

		private final Map<String, BeanProperty> properties = new LinkedHashMap<>();

		Bean(ResolvableType resolvableType, Class<?> type) {
			this.resolvableType = resolvableType;
			this.type = type;
			putProperties(type);
		}

		private void putProperties(Class<?> type) {
			while (type != null && !Object.class.equals(type)) {
				for (Method method : type.getDeclaredMethods()) {
					if (isCandidate(method)) {
						addMethod(method);
					}
				}
				for (Field field : type.getDeclaredFields()) {
					addField(field);
				}
				type = type.getSuperclass();
			}
		}

		private boolean isCandidate(Method method) {
			int modifiers = method.getModifiers();
			return Modifier.isPublic(modifiers) && !Modifier.isAbstract(modifiers)
					&& !Modifier.isStatic(modifiers)
					&& !Object.class.equals(method.getDeclaringClass())
					&& !Class.class.equals(method.getDeclaringClass());
		}

		private void addMethod(Method method) {
			addMethodIfPossible(method, "get", 0, BeanProperty::addGetter);
			addMethodIfPossible(method, "is", 0, BeanProperty::addGetter);
			addMethodIfPossible(method, "set", 1, BeanProperty::addSetter);
		}

		private void addMethodIfPossible(Method method, String prefix, int parameterCount,
				BiConsumer<BeanProperty, Method> consumer) {
			if (method.getParameterCount() == parameterCount
					&& method.getName().startsWith(prefix)
					&& method.getName().length() > prefix.length()) {
				String propertyName = Introspector
						.decapitalize(method.getName().substring(prefix.length()));
				consumer.accept(this.properties.computeIfAbsent(propertyName,
						this::getBeanProperty), method);
			}
		}

		private BeanProperty getBeanProperty(String name) {
			return new BeanProperty(name, this.resolvableType);
		}

		private void addField(Field field) {
			BeanProperty property = this.properties.get(field.getName());
			if (property != null) {
				property.addField(field);
			}
		}

		public Class<?> getType() {
			return this.type;
		}

		public Map<String, BeanProperty> getProperties() {
			return this.properties;
		}

		@SuppressWarnings("unchecked")
		public BeanSupplier<T> getSupplier(Bindable<T> target) {
			return new BeanSupplier<>(() -> {
				T instance = null;
				if (target.getValue() != null) {
					instance = target.getValue().get();
				}
				if (instance == null) {
					instance = (T) BeanUtils.instantiateClass(this.type);
				}
				return instance;
			});
		}
		// 返回一个 Bean 对象
		@SuppressWarnings("unchecked")
		public static <T> Bean<T> get(Bindable<T> bindable, boolean canCallGetValue) {
			// 获取被绑定的类的类型
			Class<?> type = bindable.getType().resolve(Object.class);
			// 封装了被绑定类的实例，之后调用 get() 方法返回被绑定类的实例
			Supplier<T> value = bindable.getValue();
			T instance = null;
			if (canCallGetValue && value != null) {
				// 获取一个被绑定类的对象
				instance = value.get();
				// 返回被绑定类的类型
				type = (instance != null) ? instance.getClass() : type;
			}
			if (instance == null && !isInstantiable(type)) {
				return null;
			}
			// 尝试到缓存里取 bean，如果 bean 不存在则实例化一个 Bean 对象放入缓存里并返回。
			Bean<?> bean = Bean.cached;
			if (bean == null || !type.equals(bean.getType())) {
				bean = new Bean<>(bindable.getType(), type);
				cached = bean;
			}
			return (Bean<T>) bean;
		}

		private static boolean isInstantiable(Class<?> type) {
			if (type.isInterface()) {
				return false;
			}
			try {
				type.getDeclaredConstructor();
				return true;
			}
			catch (Exception ex) {
				return false;
			}
		}

	}

	/**
	 * 返回一个被 @ConfigurationProperties 注解的类的实例，没有的话用工厂生产
	 * @param <T>
	 */
	private static class BeanSupplier<T> implements Supplier<T> {

		private final Supplier<T> factory;

		private T instance;

		BeanSupplier(Supplier<T> factory) {
			this.factory = factory;
		}

		@Override
		public T get() {
			if (this.instance == null) {
				this.instance = this.factory.get();
			}
			return this.instance;
		}

	}

	/**
	 * A bean property being bound.
	 *
	 * 被绑定的 bean 属性
	 *
	 */
	private static class BeanProperty {
		/**
		 * bean属性名
		 */
		private final String name;
		/**
		 * bean 属性的类型
		 */
		private final ResolvableType declaringClassType;
		/**
		 * bean 属性的 get 方法
		 */
		private Method getter;
		/**
		 * bean 属性的 set 方法
		 */
		private Method setter;
		/**
		 * 属性
		 */
		private Field field;

		BeanProperty(String name, ResolvableType declaringClassType) {
			this.name = BeanPropertyName.toDashedForm(name);
			this.declaringClassType = declaringClassType;
		}

		public void addGetter(Method getter) {
			if (this.getter == null) {
				this.getter = getter;
			}
		}

		public void addSetter(Method setter) {
			if (this.setter == null) {
				this.setter = setter;
			}
		}

		public void addField(Field field) {
			if (this.field == null) {
				this.field = field;
			}
		}

		public String getName() {
			return this.name;
		}

		public ResolvableType getType() {
			if (this.setter != null) {
				MethodParameter methodParameter = new MethodParameter(this.setter, 0);
				return ResolvableType.forMethodParameter(methodParameter,
						this.declaringClassType);
			}
			MethodParameter methodParameter = new MethodParameter(this.getter, -1);
			return ResolvableType.forMethodParameter(methodParameter,
					this.declaringClassType);
		}

		public Annotation[] getAnnotations() {
			try {
				return (this.field != null) ? this.field.getDeclaredAnnotations() : null;
			}
			catch (Exception ex) {
				return null;
			}
		}

		public Supplier<Object> getValue(Supplier<?> instance) {
			if (this.getter == null) {
				return null;
			}
			return () -> {
				try {
					this.getter.setAccessible(true);
					return this.getter.invoke(instance.get());
				}
				catch (Exception ex) {
					throw new IllegalStateException(
							"Unable to get value for property " + this.name, ex);
				}
			};
		}

		public boolean isSettable() {
			return this.setter != null;
		}
		// 用绑定器调用被绑定类的 set 方法 配置属性
		public void setValue(Supplier<?> instance, Object value) {
			try {
				this.setter.setAccessible(true);
				this.setter.invoke(instance.get(), value);
			}
			catch (Exception ex) {
				throw new IllegalStateException(
						"Unable to set value for property " + this.name, ex);
			}
		}

	}

}
