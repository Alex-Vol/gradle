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

package org.gradle.performance.generator.tasks

import org.gradle.performance.generator.*

import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.InputFiles

class JvmProjectGeneratorTask extends ProjectGeneratorTask {
    boolean groovyProject
    boolean scalaProject

    @InputFiles
    FileCollection testDependencies

    Map getTaskArgs() {
        [ groovyProject: groovyProject, scalaProject: scalaProject ]
    }

    def generateRootProject() {
        super.generateRootProject()

        project.copy {
            into(getDestDir())
            into('lib/test') {
                from testDependencies
            }
        }
    }

    void generateProjectSource(File projectDir, TestProject testProject, Map args) {
        generateProjectSource(projectDir, "java", testProject, args)
        if (groovyProject) {
            generateProjectSource(projectDir, "groovy", testProject, args)
        }
        if (scalaProject) {
            generateProjectSource(projectDir, "scala", testProject, args)
        }
    }

    void generateProjectSource(File projectDir, String sourceLang, TestProject testProject, Map args) {
        def classFilePrefix
        def classFileTemplate
        def testFilePrefix
        def testFileTemplate

        if (sourceLang == "groovy") {
            classFilePrefix = "ProductionGroovy"
            classFileTemplate = "Production.groovy"
            testFilePrefix = "TestGroovy"
            testFileTemplate = "Test.groovy"
        } else if (sourceLang == "scala") {
            classFilePrefix = "ProductionScala"
            classFileTemplate = "Production.scala"
            testFilePrefix = "TestScala"
            testFileTemplate = "Test.scala"
        } else {
            classFilePrefix = "Production"
            classFileTemplate = "Production.java"
            testFilePrefix = "Test"
            testFileTemplate = "Test.java"
        }

        def createPackageName = { fileNumber -> "org.gradle.test.performance${useSubProjectNumberInSourceFileNames ? "${testProject.subprojectNumber}_" : ''}${(int) (fileNumber / filesPerPackage) + 1}".toString() }
        def createFileName = { prefix, fileNumber -> "${prefix}${useSubProjectNumberInSourceFileNames ? "${testProject.subprojectNumber}_" : ''}${fileNumber + 1}".toString() }

        testProject.sourceFiles.times {
            String packageName = createPackageName(it)
            Map classArgs = args + [packageName: packageName, productionClassName: createFileName(classFilePrefix, it)]
            generateWithTemplate(projectDir, "src/main/${sourceLang}/${packageName.replace('.', '/')}/${classArgs.productionClassName}.${sourceLang}", classFileTemplate, classArgs)
        }
        testProject.testSourceFiles.times {
            String packageName = createPackageName(it)
            Map classArgs = args + [packageName: packageName, productionClassName: createFileName(classFilePrefix, it), testClassName: createFileName(testFilePrefix, it)]
            generateWithTemplate(projectDir, "src/test/${sourceLang}/${packageName.replace('.', '/')}/${classArgs.testClassName}.${sourceLang}", testFileTemplate, classArgs)
        }
    }
}
