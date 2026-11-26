package app.gestaoIAWpp.empresa;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface NumeroWhatsappRepository extends JpaRepository<NumeroWhatsapp, UUID> {

	Optional<NumeroWhatsapp> findByPhoneNumberIdAndAtivoTrue(String phoneNumberId);

}
