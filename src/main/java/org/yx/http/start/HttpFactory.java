/**
 * Copyright (C) 2016 - 2017 youtongluan.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 		http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.yx.http.start;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.yx.asm.AsmUtils;
import org.yx.bean.InnerIOC;
import org.yx.common.MethodInfo;
import org.yx.http.HttpHolder;
import org.yx.http.Upload;
import org.yx.http.Web;
import org.yx.http.handler.HttpInfo;
import org.yx.log.Log;
import org.yx.validate.Param;

class HttpFactory {
	private HttpNameResolver nameResolver = new HttpNameResolver();

	public void resolve(Class<?> clz) throws Exception {
		Method[] methods = clz.getMethods();
		List<Method> httpMethods = new ArrayList<>();
		for (final Method m : methods) {
			if (AsmUtils.isFilted(m.getName())) {
				continue;
			}
			if (AsmUtils.notPublicOnly(m.getModifiers())) {
				continue;
			}
			if (m.getAnnotation(Web.class) != null) {
				httpMethods.add(m);
			}
		}
		if (httpMethods.isEmpty()) {
			return;
		}
		final Object obj = InnerIOC.putClass(null, clz);
		Class<?> proxyClz = obj.getClass();
		String classFullName = clz.getName();
		for (final Method m : httpMethods) {
			Web act = m.getAnnotation(Web.class);
			Upload upload = m.getAnnotation(Upload.class);
			String soaName = nameResolver.solve(clz, m, act.value());
			Log.get("sumk.http").debug("http action-{}:{}", soaName, classFullName);
			if (HttpHolder.getHttpInfo(soaName) != null) {
				Log.get("sumk.http").error(soaName + " already existed");
				continue;
			}
			Method proxyedMethod = AsmUtils.proxyMethod(m, proxyClz);
			int argSize = m.getParameterTypes().length;
			if (argSize == 0) {
				HttpHolder.putActInfo(soaName,
						new HttpInfo(obj, proxyedMethod, null, null, null, act, upload, new Param[0]));
				continue;
			}
			Annotation[][] paramAnno = m.getParameterAnnotations();
			Param[] params = new Param[paramAnno.length];
			for (int i = 0; i < paramAnno.length; i++) {
				Annotation[] a = paramAnno[i];
				for (Annotation a2 : a) {
					if (Param.class == a2.annotationType()) {
						params[i] = (Param) a2;
						break;
					}
				}
			}
			MethodInfo mInfo = AsmUtils.createMethodInfo(classFullName, m);
			Class<?> argClz = AsmUtils.CreateArgPojo(classFullName, mInfo);
			HttpHolder.putActInfo(soaName, new HttpInfo(obj, proxyedMethod, argClz, mInfo.getArgNames(),
					m.getParameterTypes(), act, upload, params));
		}

	}

}
