/*
 * Copyright 2002-2013 SCOOP Software GmbH
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
package org.copperengine.core.test.backchannel;

public class WorkflowResult {

    private final Object result;
    private final Exception exception;

    public WorkflowResult(Object result, Exception exception) {
        super();
        this.result = result;
        this.exception = exception;
    }

    public Exception getException() {
        return exception;
    }

    public Object getResult() {
        return result;
    }

}
