package org.apache.mesos.hdfs;

import com.google.common.collect.Lists;

import org.apache.hadoop.conf.Configuration;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.Value.Type;
import org.apache.mesos.SchedulerDriver;
import org.apache.mesos.hdfs.config.HdfsFrameworkConfig;
import org.apache.mesos.hdfs.scheduler.HdfsScheduler;
import org.apache.mesos.hdfs.state.AcquisitionPhase;
import org.apache.mesos.hdfs.state.LiveState;
import org.apache.mesos.hdfs.state.IPersistentStateStore;
import org.apache.mesos.hdfs.util.DnsResolver;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import static org.mockito.Matchers.anyList;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
public class TestSchedularConstraints {

  Configuration config = new Configuration();

  private HdfsFrameworkConfig hdfsFrameworkConfig;

  @Mock
  SchedulerDriver driver;

  @Mock
  IPersistentStateStore persistenceStore;

  @Mock
  LiveState liveState;

  @Mock
  DnsResolver dnsResolver;

  @Captor
  ArgumentCaptor<Collection<Protos.TaskInfo>> taskInfosCapture;

  HdfsScheduler scheduler;

  @Test
  public void acceptOffersWithConstraintMatch() {
    when(liveState.getCurrentAcquisitionPhase()).thenReturn(
        AcquisitionPhase.DATA_NODES);
    Protos.Offer offer = addAttribute(
        createTestOfferBuilderWithResources(4, 5, 64 * 1024), "zone", "east",
        Protos.Value.Type.TEXT).build();
    config.set("mesos.hdfs.constraints", "zone:east");
    scheduler.resourceOffers(driver, Lists.newArrayList(offer));

    verify(driver, times(1)).launchTasks(anyList(), taskInfosCapture.capture());
  }

  @Test
  public void declineOffersWithNoConstraintMatch() {
    when(liveState.getCurrentAcquisitionPhase()).thenReturn(
        AcquisitionPhase.DATA_NODES);
    Protos.Offer offer = addAttribute(
        createTestOfferBuilderWithResources(4, 5, 64 * 1024), "zone", "west",
        Protos.Value.Type.TEXT).build();
    config.set("mesos.hdfs.constraints", "zone:east");
    scheduler.resourceOffers(driver, Lists.newArrayList(offer));

    verify(driver, times(1)).declineOffer(offer.getId());
  }

  @Test
  public void acceptOffersWithConstraintMatchSet() {
    when(liveState.getCurrentAcquisitionPhase()).thenReturn(
        AcquisitionPhase.DATA_NODES);
    Protos.Offer offer = addAttribute(
        createTestOfferBuilderWithResources(4, 5, 64 * 1024), "zone",
        "west,east", Protos.Value.Type.SET).build();
    config.set("mesos.hdfs.constraints", "zone:east");
    scheduler.resourceOffers(driver, Lists.newArrayList(offer));

    verify(driver, times(1)).launchTasks(anyList(), taskInfosCapture.capture());
  }

  @Test
  public void acceptOffersWithConstraintMatchScalar() {
    when(liveState.getCurrentAcquisitionPhase()).thenReturn(
        AcquisitionPhase.DATA_NODES);
    Protos.Offer offer = addAttribute(
        createTestOfferBuilderWithResources(4, 5, 64 * 1024), "CPU",
        "3.5", Protos.Value.Type.SCALAR).build();
    config.set("mesos.hdfs.constraints", "CPU:3");
    scheduler.resourceOffers(driver, Lists.newArrayList(offer));

    verify(driver, times(1)).launchTasks(anyList(), taskInfosCapture.capture());
  }

  @Test
  public void acceptOffersWithConstraintMatchMultiple() {
    when(liveState.getCurrentAcquisitionPhase()).thenReturn(
        AcquisitionPhase.DATA_NODES);
    Protos.Offer.Builder builder = createTestOfferBuilderWithResources(4, 5, 64 * 1024);
    builder = addAttribute(builder, "CPU", "3.5", Protos.Value.Type.SCALAR);
    builder = addAttribute(builder, "ZONE", "west,east,north", Protos.Value.Type.SET);
    builder = addAttribute(builder, "TYPE", "hi-end", Protos.Value.Type.TEXT);
    
    config.set("mesos.hdfs.constraints", "CPU:2;ZONE:west");
    scheduler.resourceOffers(driver, Lists.newArrayList(builder.build()));

    verify(driver, times(1)).launchTasks(anyList(), taskInfosCapture.capture());
  }
  
  @Test
  public void declineOffersWithNoConstraintMatchMultiple() {
    when(liveState.getCurrentAcquisitionPhase()).thenReturn(
        AcquisitionPhase.DATA_NODES);
    Protos.Offer.Builder builder = createTestOfferBuilderWithResources(4, 5, 64 * 1024);
    builder = addAttribute(builder, "CPU", "3.5", Protos.Value.Type.SCALAR);
    builder = addAttribute(builder, "ZONE", "west,east,north", Protos.Value.Type.SET);
    builder = addAttribute(builder, "TYPE", "hi-end", Protos.Value.Type.TEXT);
    
    Protos.Offer offer = builder.build();
    config.set("mesos.hdfs.constraints", "TYPE:low-end;ZONE:north");
    scheduler.resourceOffers(driver, Lists.newArrayList(offer));

    verify(driver, times(1)).declineOffer(offer.getId());
  }

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    hdfsFrameworkConfig = new HdfsFrameworkConfig(config);
    this.scheduler = new HdfsScheduler(hdfsFrameworkConfig, liveState,
        persistenceStore);
  }

  private Protos.OfferID createTestOfferId(int instanceNumber) {
    return Protos.OfferID.newBuilder().setValue("offer" + instanceNumber)
        .build();
  }

  private Protos.Offer.Builder createTestOfferBuilderWithResources(
      int instanceNumber, double cpus, int mem) {
    return Protos.Offer
        .newBuilder()
        .setId(createTestOfferId(instanceNumber))
        .setFrameworkId(
            Protos.FrameworkID.newBuilder().setValue("framework1").build())
        .setSlaveId(
            Protos.SlaveID.newBuilder().setValue("slave" + instanceNumber)
                .build())
        .setHostname("host" + instanceNumber)

        .addAllResources(
            Arrays
                .asList(
                    Protos.Resource
                        .newBuilder()
                        .setName("cpus")
                        .setType(Protos.Value.Type.SCALAR)
                        .setScalar(
                            Protos.Value.Scalar.newBuilder().setValue(cpus)
                                .build()).setRole("*").build(),
                    Protos.Resource
                        .newBuilder()
                        .setName("mem")
                        .setType(Protos.Value.Type.SCALAR)
                        .setScalar(
                            Protos.Value.Scalar.newBuilder().setValue(mem)
                                .build()).setRole("*").build()));
  }

  private Protos.Offer.Builder addAttribute(Protos.Offer.Builder offerBuilder,
      String attributeName, String value, Type t) {
    return offerBuilder.addAttributes(Protos.Attribute
        .newBuilder()
        .setType(t)
        .setName(attributeName)
        .setText(
            Protos.Value.Text.newBuilder()
                .setValue(t == Protos.Value.Type.TEXT ? value : "").build())
        .setScalar(
            Protos.Value.Scalar
                .newBuilder()
                .setValue(
                    t == Protos.Value.Type.SCALAR ? Double.parseDouble(value)
                        : 0.0).build())
        .setSet(
            Protos.Value.Set
                .newBuilder()
                .addAllItem(
                    new ArrayList<String>(Arrays.asList(value.split(","))))
                .build()).build());
  }
}
