/*
 * Copyright (c) 2015-2016 LabKey Corporation
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
package org.labkey.api.exp.api;

import java.util.List;
import java.util.Map;

/**
 * Records the inputs and outputs, material and/or data, for an experiment run.
 * Created by klum on 11/25/2015.
 */
public interface SimpleRunRecord
{
    Map<ExpMaterial, String> getInputMaterialMap();
    Map<ExpMaterial, String> getOutputMaterialMap();
    Map<ExpData, String> getInputDataMap();
    Map<ExpData, String> getOutputDataMap();

    ExpMaterial getAliquotInput();
    List<ExpMaterial> getAliquotOutputs();
}
