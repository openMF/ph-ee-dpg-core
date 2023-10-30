package org.mifos.pheedpgimporterrdbms.entity.variable;

import jakarta.persistence.Cacheable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.With;

@Entity
@IdClass(VariableId.class)
@Table(name = "variables")
@Cacheable(false)
@Data
@With
@NoArgsConstructor
@AllArgsConstructor
public class Variable {

    @Id
    @Column(name = "WORKFLOW_INSTANCE_KEY")
    private String workflowInstanceKey;

    @Id
    @Column(name = "NAME")
    private String name;

    @Column(name = "WORKFLOW_KEY")
    private Long workflowKey;

    @Column(name = "TIMESTAMP")
    private Long timestamp;

    @Lob
    @Column(name = "VALUE")
    private String value;

}
