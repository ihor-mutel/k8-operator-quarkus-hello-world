package org.acme;

import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSetBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.*;
import io.javaoperatorsdk.operator.processing.event.EventSourceManager;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Controller(namespaces = "default")
public class HelloWorldController  implements ResourceController<HelloWorld> {

    private final KubernetesClient kubernetesClient;

    public HelloWorldController(KubernetesClient client) {
        this.kubernetesClient = client;
    }

    @ConfigProperty(name = "quarkus.kubernetes-client.namespace")
    String namespace;

    private final Logger log = LoggerFactory.getLogger(HelloWorldController.class);

    @Override
    public DeleteControl deleteResource(HelloWorld resource, Context<HelloWorld> context) {
        return null;
    }

    @Override
    public UpdateControl<HelloWorld> createOrUpdateResource(HelloWorld helloWorldRequest, Context<HelloWorld> context) {
        final var spec = helloWorldRequest.getSpec();

        StatefulSet statefulset = kubernetesClient.apps().statefulSets().withName(spec.getName()).get();

        if(statefulset == null) {
            log.info("Create statefulset " + spec.getName());

            statefulset = new StatefulSetBuilder()
                    .withNewMetadata()
                    .withName(spec.getName())
                    .endMetadata()
                    .withNewSpec()
                    .withReplicas(1)
                    .withNewTemplate()
                    .withNewMetadata()
                    .addToLabels("app", spec.getName())
                    .endMetadata()
                    .withNewSpec()
                    .addNewContainer()
                    .withName(spec.getName())
                    .withImage(spec.getImage())
                    .withCommand("sh", "-c", "while sleep 5; do cat /tmp/data.txt; done")
                    .addNewPort()
                    .withContainerPort(80)
                    .endPort()
                    .endContainer()
                    .endSpec()
                    .endTemplate()
                    .withNewSelector()
                    .addToMatchLabels("app", spec.getName())
                    .endSelector()
                    .endSpec()
                    .build();

            kubernetesClient.apps().statefulSets().inNamespace(namespace).create(statefulset);

            return UpdateControl.noUpdate();

        } else if (statefulset.getStatus().getReplicas() < spec.getReplicas()) {

            log.info("Scale statefulset up: " + spec.getName());

            kubernetesClient.apps()
                    .statefulSets()
                    .inNamespace(namespace).withName(spec.getName()).scale(statefulset.getStatus().getReplicas() + 1, true);

            return UpdateControl.noUpdate();

        } else if (statefulset.getStatus().getReplicas() > spec.getReplicas()) {

            log.info("Scale statefulset down: " + spec.getName());

            kubernetesClient.apps()
                    .statefulSets()
                    .inNamespace(namespace).withName(spec.getName()).scale(spec.getReplicas(), true);

            return UpdateControl.noUpdate();
        }

        return UpdateControl.noUpdate();
    }

    @Override
    public void init(EventSourceManager eventSourceManager) {

    }
}
