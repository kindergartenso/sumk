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
package org.yx.http.filter;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.yx.common.TimedObject;
import org.yx.conf.AppInfo;
import org.yx.http.HttpHeadersHolder;
import org.yx.log.Log;
import org.yx.main.SumkServer;
import org.yx.util.StringUtils;

public class LocalUserSession implements UserSession {

	private Map<String, TimedObject> map = new ConcurrentHashMap<>();
	private Map<String, byte[]> keyMap = new ConcurrentHashMap<>();

	private Map<String, String> userSessionMap = new ConcurrentHashMap<>();

	@Override
	public void putKey(String sessionId, byte[] key, String userId) {
		keyMap.put(sessionId, key);
		if (StringUtils.isEmpty(userId)) {
			return;
		}
		String oldSession = userSessionMap.put(userId, sessionId);
		if (oldSession != null) {
			keyMap.remove(oldSession);
			map.remove(oldSession);
		}
	}

	public LocalUserSession() {
		Log.get("session").info("use local user session");
		SumkServer.runDeamon(() -> {
			if (SumkServer.isDestoryed()) {
				return;
			}
			Set<String> set = map.keySet();
			long now = System.currentTimeMillis();
			for (String key : set) {
				TimedObject t = map.get(key);
				if (t == null) {
					continue;
				}
				if (now > t.getEvictTime()) {
					map.remove(key);
					keyMap.remove(key);
				}
			}
			Thread.sleep(TimeUnit.MINUTES.toMillis(1));

		}, "local-session");
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T extends SessionObject> T getUserObject(Class<T> clz) {
		TimedObject to = map.get(HttpHeadersHolder.token());
		if (to == null) {
			return null;
		}
		return (T) to.getTarget();
	}

	@Override
	public void flushSession() {
		TimedObject to = map.get(HttpHeadersHolder.token());
		if (to == null) {
			return;
		}
		to.setEvictTime(System.currentTimeMillis() + AppInfo.httpSessionTimeout * 1000);

	}

	@Override
	public void setSession(String sessionId, SessionObject sessionObj) {
		TimedObject to = new TimedObject();
		to.setTarget(sessionObj);
		to.setEvictTime(System.currentTimeMillis() + AppInfo.httpSessionTimeout * 1000);
		map.put(sessionId, to);
	}

	@Override
	public void removeSession() {
		String token = HttpHeadersHolder.token();
		if (token == null) {
			return;
		}
		map.remove(token);
		keyMap.remove(token);
	}

	@Override
	public byte[] getKey(String sid) {
		return this.keyMap.get(sid);
	}

	@Override
	public void updateSession(SessionObject sessionObj) {
		String token = HttpHeadersHolder.token();
		if (token == null) {
			return;
		}
		setSession(token, sessionObj);
	}

	@Override
	public boolean isLogin(String userId) {
		if (StringUtils.isEmpty(userId)) {
			return false;
		}
		return this.userSessionMap.containsKey(userId);
	}

}
