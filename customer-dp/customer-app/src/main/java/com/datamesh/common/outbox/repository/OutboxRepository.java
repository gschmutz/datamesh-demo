package com.datamesh.common.outbox.repository;

import com.datamesh.common.outbox.model.OutboxDO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * This interface provides handles to database, to perform CRUD operations on the table `OUTBOX`.
 * The table is represented by the JPA entity {@link OutboxDO}.
 *
 * @author Sohan
 * @see JpaRepository
 */
@Repository
public interface OutboxRepository extends JpaRepository<OutboxDO, Integer> {
}
