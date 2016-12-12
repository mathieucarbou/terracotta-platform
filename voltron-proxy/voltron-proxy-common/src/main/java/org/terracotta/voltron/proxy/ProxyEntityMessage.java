/*
 * Copyright Terracotta, Inc.
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
package org.terracotta.voltron.proxy;

import org.terracotta.entity.EntityMessage;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Alex Snaps
 */
public class ProxyEntityMessage implements EntityMessage {

  private final MethodDescriptor method;
  private final Object[] args;

  private final AtomicBoolean consumed = new AtomicBoolean(false);
  private final boolean syncMessage;

  public ProxyEntityMessage(final MethodDescriptor method, final Object[] args, boolean syncMessage) {
    this.method = method;
    this.args = args;
    this.syncMessage = syncMessage;
  }

  public MethodDescriptor getMethod() {
    return method;
  }

  public Object[] getArguments() {
    return args;
  }

  public Object invoke(final Object target, final Object clientDescriptor) throws InvocationTargetException, IllegalAccessException {

    if(!consumed.compareAndSet(false, true)) {
      throw new IllegalStateException("Message was consumed already!");
    }

    if (clientDescriptor != null) {
      final Annotation[][] allAnnotations = method.getParameterAnnotations();
      for (int i = 0; i < allAnnotations.length; i++) {
        for (Annotation parameterAnnotation : allAnnotations[i]) {
          if (parameterAnnotation.annotationType() == ClientId.class) {
            args[i] = clientDescriptor;
            break;
          }
        }
      }
    }

    return method.invoke(target, args);
  }

  public Object invoke(final Object target) throws InvocationTargetException, IllegalAccessException {
    if(!consumed.compareAndSet(false, true)) {
      throw new IllegalStateException("Message was consumed already!");
    }
    return method.invoke(target, args);
  }

  public Class<?> messageType() {
    return method.getMessageType();
  }

  public int getConcurrencyKey() {
    return method.getConcurrencyKey();
  }

  public ExecutionStrategy.Location getExecutionLocation() {
    return method.getExecutionLocation();
  }

  public boolean isSyncMessage() {
    return syncMessage;
  }
}
