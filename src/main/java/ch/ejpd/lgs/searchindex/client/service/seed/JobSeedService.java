package ch.ejpd.lgs.searchindex.client.service.seed;

import ch.ejpd.lgs.searchindex.client.configuration.SedexConfiguration;
import ch.ejpd.lgs.searchindex.client.entity.type.JobType;
import ch.ejpd.lgs.searchindex.client.entity.type.TransactionState;
import ch.ejpd.lgs.searchindex.client.model.PersonData;
import ch.ejpd.lgs.searchindex.client.service.amqp.*;
import ch.ejpd.lgs.searchindex.client.service.exception.SenderIdValidationException;
import ch.ejpd.lgs.searchindex.client.service.sync.FullSyncStateManager;
import java.util.Set;
import java.util.UUID;
import lombok.NonNull;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

/**
 * Service class for seeding jobs to partial and full queues.
 */
@Service
public class JobSeedService {
  private final RabbitTemplate rabbitTemplate;
  private final QueueStatsService queueStatsService;
  private final FullSyncStateManager fullSyncStateManager;
  private static final String EMPTY_PAYLOAD = "";

  private final boolean isInMultiSenderMode;
  private final Set<String> validSenderIds;
  private final String singleSenderId;

  /**
   * Constructor for JobSeedService.
   * 
   * @param queueStatsService      Service for fetching queue statistics.
   * @param rabbitTemplate         RabbitTemplate for interacting with RabbitMQ.
   * @param fullSyncStateManager   State manager for full synchronization.
   * @param configuration          Sedex configuration containing sender information.
   */
  public JobSeedService(
      QueueStatsService queueStatsService,
      RabbitTemplate rabbitTemplate,
      FullSyncStateManager fullSyncStateManager,
      SedexConfiguration configuration) {
    this.queueStatsService = queueStatsService;
    this.rabbitTemplate = rabbitTemplate;
    this.fullSyncStateManager = fullSyncStateManager;
    this.singleSenderId = configuration.getSedexSenderId();
    this.isInMultiSenderMode = configuration.isInMultiSenderMode();
    this.validSenderIds =
        this.isInMultiSenderMode ? configuration.getSedexSenderIds() : Set.of(this.singleSenderId);
  }

  /**
   * Seed data to the partial incoming queue.
   * 
   * @param payload   Data payload to be seeded.
   * @param senderId  Sender ID for validation.
   * @return UUID of the generated transaction.
   */
  public UUID seedToPartial(@NonNull final String payload, final String senderId) {
    return seedToQueue(
        payload,
        Topics.PERSONDATA_PARTIAL_INCOMING,
        JobType.PARTIAL,
        null,
        validateOrDefaultSenderId(senderId));
  }

  /**
   * Seed data to the full incoming queue.
   * 
   * @param payload   Data payload to be seeded.
   * @param senderId  Sender ID for validation.
   * @return UUID of the generated transaction.
   */
  public UUID seedToFull(String payload, final String senderId) {
    if (fullSyncStateManager.isInStateSeeding()) {
      final UUID transactionId =
          seedToQueue(
              payload,
              Topics.PERSONDATA_FULL_INCOMING,
              JobType.FULL,
              fullSyncStateManager.getCurrentFullSyncJobId(),
              validateOrDefaultSenderId(senderId));

      fullSyncStateManager.incFullSeedMessageCounter();
      return transactionId;
    }
    return null;
  }

  private String validateOrDefaultSenderId(final String senderId) {
    if (!isInMultiSenderMode && senderId == null) {
      return singleSenderId;
    }
    if (validSenderIds.contains(senderId)) {
      return senderId;
    }
    throw new SenderIdValidationException(
        String.format(
            "Validation of senderId failed, given senderId %s, valid senderId(s): %s.",
            senderId, validSenderIds));
  }

  /**
   * Internal method for seeding data to a queue.
   * 
   * @param payload   Data payload to be seeded.
   * @param topicName Name of the topic for RabbitMQ.
   * @param jobType   Type of the job (partial or full).
   * @param jobId     ID of the job (for full synchronization).
   * @param senderId  Sender ID for validation.
   * @return UUID of the generated transaction.
   */
  private UUID seedToQueue(
      final String payload,
      final String topicName,
      final JobType jobType,
      final UUID jobId,
      final String senderId) {
    final UUID transactionId = UUID.randomUUID();
    final CommonHeadersDao headers =
        CommonHeadersDao.builder()
            .senderId(senderId)
            .jobType(jobType)
            .jobId(jobId)
            .messageCategory(MessageCategory.TRANSACTION_EVENT)
            .transactionState(TransactionState.NEW)
            .transactionId(transactionId)
            .timestamp()
            .build();

    rabbitTemplate.convertAndSend(
        Exchanges.LWGS,
        topicName,
        PersonData.builder().transactionId(transactionId).payload(payload).build(),
        headers::applyAndSetTransactionIdAsCorrelationId);

    rabbitTemplate.convertAndSend(
        Exchanges.LWGS_STATE,
        topicName,
        EMPTY_PAYLOAD,
        headers::applyAndSetTransactionIdAsCorrelationId);
    return transactionId;
  }

  public int getPartialQueued() {
    return queueStatsService.getQueueCount(Queues.PERSONDATA_PARTIAL_INCOMING);
  }

  public int getPartialProcessed() {
    return queueStatsService.getQueueCount(Queues.PERSONDATA_PARTIAL_OUTGOING);
  }

  public int getPartialFailed() {
    return queueStatsService.getQueueCount(Queues.PERSONDATA_PARTIAL_FAILED);
  }

  public int getFullQueued() {
    return queueStatsService.getQueueCount(Queues.PERSONDATA_FULL_INCOMING);
  }

  public int getFullProcessed() {
    return queueStatsService.getQueueCount(Queues.PERSONDATA_FULL_OUTGOING);
  }

  public int getFullFailed() {
    return queueStatsService.getQueueCount(Queues.PERSONDATA_FULL_FAILED);
  }
}
