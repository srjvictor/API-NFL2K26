package com.nfl.api.service;

import com.nfl.api.domain.Sponsor;
import com.nfl.api.dto.SponsorDtos.SponsorRequest;
import com.nfl.api.dto.SponsorDtos.SponsorResponse;
import com.nfl.api.dto.TeamDtos.TeamResponse;
import com.nfl.api.exception.ConflictException;
import com.nfl.api.exception.ResourceNotFoundException;
import com.nfl.api.repository.SponsorRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

@Service
@Transactional
public class SponsorService {

    private final SponsorRepository sponsorRepository;

    public SponsorService(SponsorRepository sponsorRepository) {
        this.sponsorRepository = sponsorRepository;
    }

    @Transactional(readOnly = true)
    public Page<SponsorResponse> findAll(Pageable pageable) {
        return sponsorRepository.findAll(pageable).map(SponsorResponse::from);
    }

    @Transactional(readOnly = true)
    public SponsorResponse findById(Long id) {
        return SponsorResponse.from(getSponsor(id));
    }

    @Transactional(readOnly = true)
    public Page<SponsorResponse> search(String industry, BigDecimal minValue, Pageable pageable) {
        return sponsorRepository.search(industry == null ? "" : industry.trim(),
                minValue == null ? BigDecimal.ZERO : minValue, pageable).map(SponsorResponse::from);
    }

    /** Times patrocinados (lado inverso do Many-to-Many), paginados em memória. */
    @Transactional(readOnly = true)
    public Page<TeamResponse> findTeams(Long sponsorId, Pageable pageable) {
        List<TeamResponse> all = getSponsor(sponsorId).getTeams().stream()
                .map(TeamResponse::from)
                .sorted(Comparator.comparing(TeamResponse::id))
                .toList();
        int start = (int) Math.min(pageable.getOffset(), all.size());
        int end = Math.min(start + pageable.getPageSize(), all.size());
        return new PageImpl<>(all.subList(start, end), pageable, all.size());
    }

    public SponsorResponse create(SponsorRequest request) {
        if (sponsorRepository.existsByNameIgnoreCase(request.name())) {
            throw new ConflictException("Já existe um patrocinador com o nome '" + request.name() + "'");
        }
        Sponsor sponsor = new Sponsor();
        apply(sponsor, request);
        return SponsorResponse.from(sponsorRepository.save(sponsor));
    }

    public SponsorResponse update(Long id, SponsorRequest request) {
        Sponsor sponsor = getSponsor(id);
        if (!sponsor.getName().equalsIgnoreCase(request.name())
                && sponsorRepository.existsByNameIgnoreCase(request.name())) {
            throw new ConflictException("Já existe um patrocinador com o nome '" + request.name() + "'");
        }
        apply(sponsor, request);
        return SponsorResponse.from(sponsor);
    }

    public void delete(Long id) {
        Sponsor sponsor = getSponsor(id);
        // Team é o lado dono do Many-to-Many: remove o patrocinador de cada time antes de excluir
        sponsor.getTeams().forEach(team -> team.getSponsors().remove(sponsor));
        sponsor.getTeams().clear();
        sponsorRepository.delete(sponsor);
    }

    private Sponsor getSponsor(Long id) {
        return sponsorRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Patrocinador", id));
    }

    private void apply(Sponsor s, SponsorRequest r) {
        s.setName(r.name());
        s.setIndustry(r.industry());
        s.setAnnualValueUsd(r.annualValueUsd());
        s.setWebsite(r.website());
    }
}
