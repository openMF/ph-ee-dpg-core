package org.mifos.pheedpgimporterrdbms.entity.variable;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.With;

@Data
@With
@NoArgsConstructor
@AllArgsConstructor
public class VariableId implements Serializable {

    private String workflowInstanceKey;
    private String name;

}
