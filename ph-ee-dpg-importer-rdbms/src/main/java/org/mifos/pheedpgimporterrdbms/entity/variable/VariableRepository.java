package org.mifos.pheedpgimporterrdbms.entity.variable;

import java.util.List;
import org.springframework.data.repository.CrudRepository;

public interface VariableRepository extends CrudRepository<Variable, String> {

    List<Variable> findByWorkflowInstanceKey(String workflowInstanceKey);

}
