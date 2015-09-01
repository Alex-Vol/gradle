/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.internal.project.antbuilder

import org.gradle.api.internal.ClassPathRegistry
import org.gradle.api.internal.DefaultClassPathProvider
import org.gradle.api.internal.DefaultClassPathRegistry
import org.gradle.api.internal.classpath.DefaultModuleRegistry
import org.gradle.api.internal.classpath.ModuleRegistry
import org.gradle.internal.classloader.DefaultClassLoaderFactory
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.Shared
import spock.lang.Specification

import java.lang.reflect.Proxy

@Requires(TestPrecondition.JDK7_OR_LATER)
class AntBuilderMemoryLeakTest extends Specification {
    @Shared
    private ModuleRegistry moduleRegistry = new DefaultModuleRegistry()

    @Shared
    private ClassPathRegistry registry = new DefaultClassPathRegistry(new DefaultClassPathProvider(moduleRegistry))

    @Shared
    private DefaultClassLoaderFactory classLoaderFactory = new DefaultClassLoaderFactory()

    def "should release cache when cleanup is called"() {
        classLoaderFactory = new DefaultClassLoaderFactory()
        def builder = new DefaultIsolatedAntBuilder(registry, classLoaderFactory)

        when:
        builder.withClasspath([new File('foo')]).execute {
            // do something
        }

        then:
        builder.classLoaderCache.size() == 1

        when:
        builder.classLoaderCache.shutdown()

        then:
        builder.classLoaderCache.isEmpty()
    }

    def "should release cache under memory pressure"() {
        given:
        def builder = new DefaultIsolatedAntBuilder(registry, classLoaderFactory)
        def classes = []

        when:
        int i = 0
        // time out after 10 minutes
        long maxTime = System.currentTimeMillis() + 10*60*1000
        try {
            while (System.currentTimeMillis()<maxTime) {
                builder.withClasspath([new File("foo$i")]).execute {

                }
                classes << Proxy.getProxyClass(classLoaderFactory.createIsolatedClassLoader([]), Serializable)
                i++
            }
        } catch (OutOfMemoryError e) {
            classes = []
        }

        then:
        assert i>0
        assert classes.empty
        builder.classLoaderCache.size() < i-1
    }
}
