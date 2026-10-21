package tech.kayys.school.service;

import java.time.LocalDate;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import tech.kayys.school.model.AcaraSekolah;
import tech.kayys.school.model.Kelas;

@ApplicationScoped
public class EventService {
    
    @Transactional
    public AcaraSekolah createEvent(AcaraSekolah acara) {
        acara.persist();
        return acara;
    }
    
    @Transactional
    public void updateEventStatus(Long eventId, AcaraSekolah.StatusAcara status) {
        AcaraSekolah acara = AcaraSekolah.findById(eventId);
        if (acara == null) {
            throw new IllegalArgumentException("Event not found with ID: " + eventId);
        }
        
        acara.statusAcara = status;
    }
    
    @Transactional
    public void cancelEvent(Long eventId) {
        updateEventStatus(eventId, AcaraSekolah.StatusAcara.DIBATALKAN);
    }
    
    public List<AcaraSekolah> getUpcomingEvents() {
        return AcaraSekolah.findUpcomingEvents();
    }
    
    public List<AcaraSekolah> getEventsByDate(LocalDate date) {
        return AcaraSekolah.findByTanggal(date);
    }
    
    public List<AcaraSekolah> getEventsByClass(Kelas kelas) {
        return AcaraSekolah.list("kelas", kelas);
    }
}