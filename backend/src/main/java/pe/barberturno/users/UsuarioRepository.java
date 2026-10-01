package pe.barberturno.users;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repositorio de Usuario. */
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    /** Bloquea el cliente antes de los barberos y de la reserva (arquitectura §8). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from Usuario u where u.id = :id")
    Optional<Usuario> bloquearPorId(@Param("id") Long id);
}
