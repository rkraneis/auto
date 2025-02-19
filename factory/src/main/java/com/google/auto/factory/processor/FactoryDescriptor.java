/*
 * Copyright (C) 2013 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.auto.factory.processor;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.auto.common.MoreTypes;
import com.google.auto.value.AutoValue;
import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map.Entry;

import javax.lang.model.type.TypeMirror;

/**
 * A value object representing a factory to be generated.
 *
 * @author Gregory Kick
 */
@AutoValue
abstract class FactoryDescriptor {
  private static final CharMatcher invalidIdentifierCharacters =
      new CharMatcher() {
        @Override
        public boolean matches(char c) {
          return !Character.isJavaIdentifierPart(c);
        }
      };

  abstract String name();
  abstract TypeMirror extendingType();
  abstract ImmutableSet<TypeMirror> implementingTypes();
  abstract boolean publicType();
  abstract ImmutableSet<FactoryMethodDescriptor> methodDescriptors();
  abstract ImmutableSet<ImplementationMethodDescriptor> implementationMethodDescriptors();
  abstract boolean allowSubclasses();
  abstract ImmutableMap<Key, String> providerNames();

  static FactoryDescriptor create(
      String name,
      TypeMirror extendingType,
      ImmutableSet<TypeMirror> implementingTypes,
      boolean publicType,
      ImmutableSet<FactoryMethodDescriptor> methodDescriptors,
      ImmutableSet<ImplementationMethodDescriptor> implementationMethodDescriptors,
      boolean allowSubclasses) {
    ImmutableSetMultimap.Builder<Key, String> providerNamesBuilder = ImmutableSetMultimap.builder();
    for (FactoryMethodDescriptor descriptor : methodDescriptors) {
      for (Parameter parameter : descriptor.providedParameters()) {
        providerNamesBuilder.put(parameter.key(), parameter.name());
      }
    }
    ImmutableMap.Builder<Key, String> providersBuilder = ImmutableMap.builder();
    for (Entry<Key, Collection<String>> entry : providerNamesBuilder.build().asMap().entrySet()) {
      Key key = entry.getKey();
      switch (entry.getValue().size()) {
        case 0:
          throw new AssertionError();
        case 1:
          providersBuilder.put(key, Iterables.getOnlyElement(entry.getValue()) + "Provider");
          break;
        default:
          providersBuilder.put(
              key, invalidIdentifierCharacters.replaceFrom(key.toString(), '_') + "Provider");
          break;
      }
    }
    return new AutoValue_FactoryDescriptor(
        name,
        extendingType,
        implementingTypes,
        publicType,
        methodDescriptors,
        dedupeMethods(methodDescriptors, implementationMethodDescriptors),
        allowSubclasses,
        providersBuilder.build());
  }

  /**
   * Removes methods with matching signatures from the set of
   * {@link ImplementationMethodDescriptor}s.
   */
  private static ImmutableSet<ImplementationMethodDescriptor> dedupeMethods(
      ImmutableSet<FactoryMethodDescriptor> methodDescriptors,
      ImmutableSet<ImplementationMethodDescriptor> implementationMethodDescriptors) {

    checkNotNull(implementationMethodDescriptors);
    LinkedHashSet<ImplementationMethodDescriptor> dedupedMethods =
        new LinkedHashSet<ImplementationMethodDescriptor>(implementationMethodDescriptors);

    for (ImplementationMethodDescriptor implementationMethod : implementationMethodDescriptors) {
      for (FactoryMethodDescriptor factoryMethod : methodDescriptors) {
        if (implementationMethod.name().equals(factoryMethod.name())
            && parameterTypesEqual(
                implementationMethod.passedParameters(), factoryMethod.passedParameters())) {
          dedupedMethods.remove(implementationMethod);
        }
      }
    }
    return ImmutableSet.copyOf(dedupedMethods);
  }

  /**
   * Returns whether the two {@link Iterable}s of {@link Parameter}s are equal solely by type.
   */
  private static boolean parameterTypesEqual(
      Iterable<Parameter> first, Iterable<Parameter> second) {

    return MoreTypes.equivalence().pairwise().equivalent(
        Iterables.transform(first, Parameter.parameterToType),
        Iterables.transform(second, Parameter.parameterToType));
  }
}
