package org.mifos.pheedpgimporterrdbms.streams;

import java.util.Optional;
import org.mifos.pheedpgimporterrdbms.config.TransferTransformerConfig;
import org.mifos.pheedpgimporterrdbms.entity.transfer.Transfer;
import org.mifos.pheedpgimporterrdbms.entity.transfer.TransferRepository;
import org.mifos.pheedpgimporterrdbms.entity.transfer.TransferStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class InFlightTransferManager {

    @Autowired
    TransferTransformerConfig transferTransformerConfig;

    @Autowired
    TransferRepository transferRepository;

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public Transfer retrieveOrCreateTransfer(String bpmn, String processInstanceKey) {
        // Long processInstanceKey = record.read("$.value.processInstanceKey", Long.class);
        Optional<TransferTransformerConfig.Flow> config = transferTransformerConfig.findFlow(bpmn);
        Transfer transfer = transferRepository.findByWorkflowInstanceKey(processInstanceKey);
        if (transfer == null) {
            logger.debug("creating new Transfer for processInstanceKey: {}", processInstanceKey);
            transfer = new Transfer(processInstanceKey);
            transfer.setStatus(TransferStatus.IN_PROGRESS);

            if (config.isPresent()) {
                transfer.setDirection(config.get().getDirection());
            } else {
                logger.error("No config found for bpmn: {}", bpmn);
            }
            transferRepository.save(transfer);
        } else {
            logger.info("found existing Transfer for processInstanceKey: {}", processInstanceKey);
        }
        return transfer;
    }
}
