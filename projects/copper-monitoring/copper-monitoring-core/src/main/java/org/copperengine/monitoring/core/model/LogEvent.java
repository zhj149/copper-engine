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
package org.copperengine.monitoring.core.model;

import java.io.Serializable;
import java.util.Date;

public class LogEvent implements Serializable, MonitoringData {
    private static final long serialVersionUID = -3392518179689121117L;

    private Date time;
    private String message;
    private String level;
    private String locationInformation;

    @Override
    public Date getTimeStamp() {
        return time;
    }

    public void setTime(Date time) {
        this.time = time;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public LogEvent(Date time, String message, String locationInformation, String level) {
        super();
        this.time = time;
        this.message = message;
        this.level = level;
        this.locationInformation = locationInformation;
    }

    public LogEvent() {
        super();
    }

    public String getLocationInformation() {
        return locationInformation;
    }

    public void setLocationInformation(String locationInformation) {
        this.locationInformation = locationInformation;
    }

}
