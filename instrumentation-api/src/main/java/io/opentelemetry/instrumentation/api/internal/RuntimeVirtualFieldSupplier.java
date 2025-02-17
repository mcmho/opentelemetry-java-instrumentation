/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import io.opentelemetry.instrumentation.api.cache.Cache;
import io.opentelemetry.instrumentation.api.field.VirtualField;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RuntimeVirtualFieldSupplier {

  private static final Logger logger = LoggerFactory.getLogger(RuntimeVirtualFieldSupplier.class);

  public interface VirtualFieldSupplier {
    <U extends T, T, F> VirtualField<U, F> find(Class<T> type, Class<F> fieldType);
  }

  private static final VirtualFieldSupplier DEFAULT = new CacheBasedVirtualFieldSupplier();

  private static volatile VirtualFieldSupplier instance = DEFAULT;

  public static void set(VirtualFieldSupplier virtualFieldSupplier) {
    // only overwrite the default, cache-based supplier
    if (instance != DEFAULT) {
      logger.warn(
          "Runtime VirtualField supplier has already been set up, further set() calls are ignored");
      return;
    }
    instance = virtualFieldSupplier;
  }

  public static VirtualFieldSupplier get() {
    return instance;
  }

  private static final class CacheBasedVirtualFieldSupplier implements VirtualFieldSupplier {

    private final Cache<Class<?>, Cache<Class<?>, VirtualField<?, ?>>>
        ownerToFieldToImplementationMap = Cache.weak();

    @Override
    @SuppressWarnings("unchecked")
    public <U extends T, T, F> VirtualField<U, F> find(Class<T> type, Class<F> fieldType) {
      return (VirtualField<U, F>)
          ownerToFieldToImplementationMap
              .computeIfAbsent(type, c -> Cache.weak())
              .computeIfAbsent(fieldType, c -> new CacheBasedVirtualField<>());
    }
  }

  private static final class CacheBasedVirtualField<T, F> extends VirtualField<T, F> {
    private final Cache<T, F> cache = Cache.weak();

    @Override
    @Nullable
    public F get(T object) {
      return cache.get(object);
    }

    @Override
    public void set(T object, @Nullable F fieldValue) {
      if (fieldValue == null) {
        cache.remove(object);
      } else {
        cache.put(object, fieldValue);
      }
    }
  }

  private RuntimeVirtualFieldSupplier() {}
}
