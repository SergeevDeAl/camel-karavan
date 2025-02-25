/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.camel.karavan.kubernetes;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.vertx.core.json.JsonObject;
import io.vertx.mutiny.core.eventbus.EventBus;
import org.apache.camel.karavan.model.ContainerType;
import org.apache.camel.karavan.model.DeploymentStatus;
import org.jboss.logging.Logger;

import static org.apache.camel.karavan.KaravanConstants.LABEL_TYPE;
import static org.apache.camel.karavan.KaravanEvents.DEPLOYMENT_DELETED;
import static org.apache.camel.karavan.KaravanEvents.DEPLOYMENT_UPDATED;

public class DeploymentEventHandler implements ResourceEventHandler<Deployment> {

    private static final Logger LOGGER = Logger.getLogger(DeploymentEventHandler.class.getName());
    private final KubernetesStatusService kubernetesStatusService;
    private final EventBus eventBus;

    public DeploymentEventHandler(KubernetesStatusService kubernetesStatusService, EventBus eventBus) {
        this.kubernetesStatusService = kubernetesStatusService;
        this.eventBus = eventBus;
    }

    @Override
    public void onAdd(Deployment deployment) {
        try {
            LOGGER.info("onAdd " + deployment.getMetadata().getName());
            DeploymentStatus ds = getDeploymentStatus(deployment);
            eventBus.publish(DEPLOYMENT_UPDATED, JsonObject.mapFrom(ds));
        } catch (Exception e){
            LOGGER.error(e.getMessage());
        }
    }

    @Override
    public void onUpdate(Deployment oldDeployment, Deployment newDeployment) {
        try {
            LOGGER.info("onUpdate " + newDeployment.getMetadata().getName());
            DeploymentStatus ds = getDeploymentStatus(newDeployment);
            eventBus.publish(DEPLOYMENT_UPDATED, JsonObject.mapFrom(ds));
        } catch (Exception e){
            LOGGER.error(e.getMessage());
        }
    }

    @Override
    public void onDelete(Deployment deployment, boolean deletedFinalStateUnknown) {
        try {
            LOGGER.info("onDelete " + deployment.getMetadata().getName());
            DeploymentStatus ds = new DeploymentStatus(
                    deployment.getMetadata().getName(),
                    deployment.getMetadata().getNamespace(),
                    kubernetesStatusService.getCluster(),
                    kubernetesStatusService.getEnvironment());
            eventBus.publish(DEPLOYMENT_DELETED, JsonObject.mapFrom(ds));
        } catch (Exception e){
            LOGGER.error(e.getMessage());
        }
    }

    public DeploymentStatus getDeploymentStatus(Deployment deployment) {
        try {
            var dsImage = deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getImage();
            var imageName = dsImage.startsWith("image-registry.openshift-image-registry.svc")
                    ? dsImage.replace("image-registry.openshift-image-registry.svc:5000/", "")
                    : dsImage;
            var typeLabel = deployment.getMetadata().getLabels().get(LABEL_TYPE);
            var type = typeLabel != null ? ContainerType.valueOf(typeLabel) : ContainerType.unknown;
            return new DeploymentStatus(
                    deployment.getMetadata().getName(),
                    deployment.getMetadata().getNamespace(),
                    kubernetesStatusService.getCluster(),
                    kubernetesStatusService.getEnvironment(),
                    imageName,
                    deployment.getSpec().getReplicas(),
                    deployment.getStatus().getReadyReplicas(),
                    deployment.getStatus().getUnavailableReplicas(),
                    type
            );
        } catch (Exception ex) {
            LOGGER.error(ex.getMessage());
            return new DeploymentStatus(
                    deployment.getMetadata().getName(),
                    deployment.getMetadata().getNamespace(),
                    kubernetesStatusService.getCluster(),
                    kubernetesStatusService.getEnvironment());
        }
    }
}