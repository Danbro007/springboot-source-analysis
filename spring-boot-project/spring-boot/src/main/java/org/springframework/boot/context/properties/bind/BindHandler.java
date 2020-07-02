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

import org.springframework.boot.context.properties.source.ConfigurationPropertyName;

/**
 * Callback interface that can be used to handle additional logic during element
 * {@link Binder binding}.
 *
 * 在元素绑定期间使用这个回调接口来处理额外的逻辑
 *
 * @author Phillip Webb
 * @author Madhura Bhave
 * @since 2.0.0
 */
public interface BindHandler {

	/**
	 * Default no-op bind handler.
	 */
	BindHandler DEFAULT = new BindHandler() {

	};

	/**
	 * Called when binding of an element starts but before any result has been determined.
	 *
	 * 在元素绑定开始但尚未确定结果时调用。
	 *
	 * @param <T> the bindable source type
	 * @param name the name of the element being bound
	 * @param target the item being bound
	 * @param context the bind context
	 * @return the actual item that should be used for binding (may be {@code null})
	 */
	default <T> Bindable<T> onStart(ConfigurationPropertyName name, Bindable<T> target,
			BindContext context) {
		return target;
	}

	/**
	 * Called when binding of an element ends with a successful result. Implementations
	 * may change the ultimately returned result or perform addition validation.
	 * @param name the name of the element being bound
	 * @param target the item being bound
	 * @param context the bind context
	 * @param result the bound result (never {@code null})
	 * @return the actual result that should be used (may be {@code null})
	 */
	default Object onSuccess(ConfigurationPropertyName name, Bindable<?> target,
			BindContext context, Object result) {
		return result;
	}

	/**
	 * Called when binding fails for any reason (including failures from
	 * {@link #onSuccess} calls). Implementations may choose to swallow exceptions and
	 * return an alternative result.
	 *
	 * 当绑定因任何原因(包括 onSuccess() 调用失败)失败时调用。实现可以选择吞下异常，返回一个可选的结果。
	 *
	 * @param name the name of the element being bound
	 * @param target the item being bound
	 * @param context the bind context
	 * @param error the cause of the error (if the exception stands it may be re-thrown)
	 * @return the actual result that should be used (may be {@code null}).
	 * @throws Exception if the binding isn't valid
	 */
	default Object onFailure(ConfigurationPropertyName name, Bindable<?> target,
			BindContext context, Exception error) throws Exception {
		throw error;
	}

	/**
	 * Called when binding finishes, regardless of whether the property was bound or not.
	 *
	 * 在绑定结束时调用，无论属性是否绑定。
	 *
	 * @param name the name of the element being bound
	 * @param target the item being bound
	 * @param context the bind context
	 * @param result the bound result (may be {@code null})
	 * @throws Exception if the binding isn't valid
	 */
	default void onFinish(ConfigurationPropertyName name, Bindable<?> target,
			BindContext context, Object result) throws Exception {
	}

}
