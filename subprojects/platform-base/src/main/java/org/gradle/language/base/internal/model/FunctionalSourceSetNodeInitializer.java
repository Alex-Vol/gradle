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

package org.gradle.language.base.internal.model;

import org.gradle.api.Nullable;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.language.base.FunctionalSourceSet;
import org.gradle.language.base.ProjectSourceSet;
import org.gradle.language.base.internal.DefaultFunctionalSourceSet;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.type.ModelType;

import java.util.Collections;
import java.util.List;

public class FunctionalSourceSetNodeInitializer implements NodeInitializer {
    private final Instantiator instantiator;

    public FunctionalSourceSetNodeInitializer(Instantiator instantiator) {
        this.instantiator = instantiator;
    }

    @Override
    public List<? extends ModelReference<?>> getInputs() {
        return Collections.singletonList(ModelReference.of(ProjectSourceSet.class));
    }

    @Override
    public void execute(MutableModelNode modelNode, List<ModelView<?>> inputs) {
        ProjectSourceSet projectSourceSet = (ProjectSourceSet) inputs.get(0).getInstance();
        DefaultFunctionalSourceSet defaultFunctionalSourceSet = new DefaultFunctionalSourceSet(modelNode.getPath().getName(), instantiator, projectSourceSet);
        modelNode.setPrivateData(DefaultFunctionalSourceSet.class, defaultFunctionalSourceSet);
    }

    @Override
    public List<? extends ModelProjection> getProjections() {
        return Collections.singletonList(new UnmanagedModelProjection<FunctionalSourceSet>(ModelType.of(FunctionalSourceSet.class)));
    }

    @Nullable
    @Override
    public ModelAction getProjector(ModelPath path, ModelRuleDescriptor descriptor) {
        return null;
    }
}
