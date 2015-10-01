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

package org.gradle.model.internal.inspect

import org.gradle.model.internal.ModelValidationTypes
import org.gradle.model.internal.core.DefaultNodeInitializerRegistry
import org.gradle.model.internal.core.ModelCreators
import org.gradle.model.internal.core.ModelRuleExecutionException
import org.gradle.model.internal.core.ModelTypeInitializationException
import org.gradle.model.internal.fixture.ModelRegistryHelper
import org.gradle.model.internal.manage.schema.extract.DefaultModelSchemaStore
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class ManagedModelInitializerTest extends Specification implements ModelValidationTypes {

    @Shared
    def store = DefaultModelSchemaStore.getInstance()
    def r = new ModelRegistryHelper()
    def nodeInitializerRegistry = new DefaultNodeInitializerRegistry(store)

    def "must be symmetrical"() {
        expect:
        failWhenRealized ModelValidationTypes.OnlyGetter, "read only property 'name' has non managed type java.lang.String, only managed types can be used"
    }

    @Unroll
    def "only selected unmanaged property types are allowed"() {
        expect:
        failWhenRealized(type,
            canNotBeConstructed("${failingProperty.name}"),
            "Its type must be one the following:",
            "- A supported scalar type (",
            "- An enumerated type (Enum)",
            "- An explicitly managed type (i.e. annotated with @Managed)",
            "- An explicitly unmanaged type (i.e. annotated with @Unmanaged)",
            "- A scalar collection type ("
        )

        where:
        type                                           | failingProperty
        ModelValidationTypes.NonStringProperty         | Object
        ModelValidationTypes.ClassWithExtendedFileType | ModelValidationTypes.ExtendedFile
    }

    def "unmanaged types must be annotated with unmanaged"() {
        expect:
        failWhenRealized(ModelValidationTypes.MissingUnmanaged,
            canNotBeConstructed('java.io.InputStream'),
            "- An explicitly unmanaged type (i.e. annotated with @Unmanaged)")
    }

    @Unroll
    def "should enforce properties of #type are managed"() {
        when:
        Class<?> generatedClass = managedClassWithoutSetter(type)

        then:
        failWhenRealized generatedClass, "has non managed type ${type.name}, only managed types can be used"

        where:
        type << JDK_SCALAR_TYPES
    }

    def "model map type must be managed in a managed type"() {
        expect:
        failWhenRealized(ModelValidationTypes.UnmanagedModelMapInManagedType,
            canNotBeConstructed(InputStream.name),
            "- A managed collection type (ModelMap<?>, ManagedSet<?>, ModelSet<?>)"
        )
    }

    @Unroll
    def "must have a setter - #managedType.simpleName"() {
        when:
        failWhenRealized(managedType, "Invalid managed model type '$managedType.name': read only property 'thing' has non managed type boolean, only managed types can be used")

        then:
        true

        where:
        managedType << [ModelValidationTypes.OnlyIsGetter, ModelValidationTypes.OnlyGetGetter]
    }

    @Unroll
    def "throws an error if we use unsupported collection type #collectionType.simpleName"() {
        when:
        def managedType = new GroovyClassLoader(getClass().classLoader).parseClass """
            import org.gradle.model.Managed

            @Managed
            interface CollectionType {
                ${collectionType.name}<String> getItems()
            }
        """

        then:
        failWhenRealized(managedType, canNotBeConstructed("${collectionType.name}<java.lang.String>"),
            "- A scalar collection type (List, Set)",
            "- A managed collection type (ModelMap<?>, ManagedSet<?>, ModelSet<?>)")


        where:
        collectionType << [LinkedList, ArrayList, SortedSet, TreeSet]
    }

    def "type of the first argument of void returning model definition has to be @Managed annotated"() {
        expect:
        failWhenRealized(NonManaged,
            canNotBeConstructed("$NonManaged.name"),
            "- An explicitly managed type (i.e. annotated with @Managed)")
    }

    private void failWhenRealized(Class type, String... expectedMessages) {
        try {
            r.create(ModelCreators.of(r.path("bar"), nodeInitializerRegistry.getNodeInitializer(store.getSchema(type))).descriptor(r.desc("bar")).build())
            r.realize("bar", type)
            throw new AssertionError("node realisation of type ${getName(type)} should have failed with a cause of:\n$expectedMessages\n")
        }
        catch (ModelRuleExecutionException e) {
            assertExpected(e.cause, expectedMessages)
        } catch (ModelTypeInitializationException e) {
            assertExpected(e, expectedMessages)
        }
    }

    private void assertExpected(Exception e, String... expectedMessages) {
        expectedMessages.each { String error ->
            assert e.message.contains(error)
        }
    }
}
