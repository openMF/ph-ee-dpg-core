package org.mifos.connector.ams.interop;

import org.apache.camel.Exchange;
import org.springframework.stereotype.Service;

@Service
public interface AmsService {

    void getExternalAccount(Exchange e);

    void sendTransfer(Exchange e);

}
