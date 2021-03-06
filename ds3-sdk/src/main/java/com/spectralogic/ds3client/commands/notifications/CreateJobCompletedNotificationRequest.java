/*
 * ******************************************************************************
 *   Copyright 2014-2015 Spectra Logic Corporation. All Rights Reserved.
 *   Licensed under the Apache License, Version 2.0 (the "License"). You may not use
 *   this file except in compliance with the License. A copy of the License is located at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *   or in the "license" file accompanying this file.
 *   This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 *   CONDITIONS OF ANY KIND, either express or implied. See the License for the
 *   specific language governing permissions and limitations under the License.
 * ****************************************************************************
 */

package com.spectralogic.ds3client.commands.notifications;

import java.util.UUID;

/**
 * Creates a NotificationRequest to receive notifications when either a specific job is completed
 * or when any job completes.
 */
public class CreateJobCompletedNotificationRequest extends AbstractCreateNotificationRequest {
    /**
     * Create a NotificationRequest that will receive all JobCompleted Notifications
     */
    public CreateJobCompletedNotificationRequest(final String endpoint) {
        super(endpoint);
    }

    /**
     * Create a NotificationRequest that will receive the JobCompleted Notification only for
     * the job specified by {@param jobId}
     */
    public CreateJobCompletedNotificationRequest(final String endpoint, final UUID jobId) {
        super(endpoint);
        this.getQueryParams().put("job_id", jobId.toString());
    }

    @Override
    public String getPath() {
        return "/_rest_/job_completed_notification_registration";
    }
}
