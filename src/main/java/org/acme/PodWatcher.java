package org.acme;


import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import io.quarkus.runtime.StartupEvent;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.event.Observes;
import java.io.IOException;
import java.util.List;
import java.util.Map;


public class PodWatcher {

    @ConfigProperty(name = "quarkus.kubernetes-client.namespace")
    String namespace;

    ObjectMapper mapper = new ObjectMapper();

    private final KubernetesClient kubernetesClient;

    public PodWatcher(KubernetesClient kubernetesClient) {
        this.kubernetesClient = kubernetesClient;
    }

    private final Logger log = LoggerFactory.getLogger(PodWatcher.class);



    void onStartup(@Observes StartupEvent startupEvent) throws IOException {

        List<Pod> pods = kubernetesClient.pods().inNamespace(namespace).list().getItems();

        kubernetesClient.pods().watch(new Watcher<Pod>() {

            @Override
            public void eventReceived(Action action, Pod pod) {

                log.info("Received " + action + ", pod name " + pod.getMetadata().getName());

                CustomResourceDefinitionContext helloWorldCustomResource = CustomResourceDefinitionContext.fromCustomResourceType(HelloWorld.class);
                Map<String, Object> cr = kubernetesClient.customResource(helloWorldCustomResource).get(namespace, "hello-world-example");
                HelloWorldSpec spec = mapper.convertValue(cr.get("spec"), HelloWorldSpec.class);

                if (action == Action.ADDED && pod.getMetadata().getName().contains(spec.getName())) {

                    StatefulSet statefulset = kubernetesClient.apps().statefulSets().withName(spec.getName()).get();

                    String podName = pod.getMetadata().getName();

                    try {
                        Thread.sleep(10 * 1000L);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    log.info("Get pod logs " + pod.getMetadata().getName());

                    String logs = kubernetesClient.pods()
                            .inNamespace(namespace)
                            .withName(podName)
                            .getLog();

                    log.info("Check if data is available in pod " + pod.getMetadata().getName());

                    if (!logs.contains("Example of injected data")) {
                        log.info("Inject data into pod " + podName);
                        newExecWatch(kubernetesClient, namespace, podName, spec.getData());
                    }

                    try {
                        Thread.sleep(10 * 1000L);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    logs = kubernetesClient.pods()
                            .inNamespace(pod.getMetadata().getNamespace())
                            .withName(pod.getMetadata().getName())
                            .getLog();

                    if (logs.contains("Example of injected data")) {

                        log.info("Data is available in pod " + pod.getMetadata().getName());

                        if (spec.getReplicas() > statefulset.getStatus().getReplicas()) {

                            log.info("Scale statefulset size, current size " + spec.getName());

                            try {
                                scaleStatefulSet(namespace, spec.getName(), statefulset.getStatus().getReplicas());
                            } catch (Exception ex) {
                                log.error(ex.getMessage());
                            }


                        }
                        {
                            log.info("Statefulset size: " + statefulset.getStatus().getReplicas() + ", desired: " + statefulset.getStatus().getReplicas());
                        }


                    }

                }

            }

            @Override
            public void onClose(WatcherException e) {

            }
        });


    }

    private ExecWatch newExecWatch(KubernetesClient client, String namespace, String podName, String data) {
        return client.pods().inNamespace(namespace).withName(podName)
                .readingInput(System.in)
                .writingOutput(System.out)
                .writingError(System.err)
                .withTTY()
                .usingListener(new SimpleListener())
                .exec("sh", "-c", "echo \"" + data + "\" > /tmp/data.txt");
    }

    void scaleStatefulSet(String namespace, String name, int replicaSize) {

        log.info("Scale statefulset " + name);
        kubernetesClient.apps().statefulSets().inNamespace(namespace).withName(name).scale(replicaSize + 1, true);

    }

}



