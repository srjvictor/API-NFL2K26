package com.nfl.api.service;

import com.nfl.api.domain.Sponsor;
import com.nfl.api.domain.Stadium;
import com.nfl.api.domain.Team;
import com.nfl.api.domain.enums.Conference;
import com.nfl.api.domain.enums.Division;
import com.nfl.api.dto.PlayerDtos.PlayerResponse;
import com.nfl.api.dto.SponsorDtos.SponsorResponse;
import com.nfl.api.dto.TeamDtos.TeamRequest;
import com.nfl.api.dto.TeamDtos.TeamResponse;
import com.nfl.api.exception.ConflictException;
import com.nfl.api.exception.ResourceNotFoundException;
import com.nfl.api.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TeamService {

    private final TeamRepository teamRepository;
    private final StadiumRepository stadiumRepository;
    private final SponsorRepository sponsorRepository;
    private final PlayerRepository playerRepository;
    private final GameRepository gameRepository;

    public TeamService(TeamRepository teamRepository, StadiumRepository stadiumRepository,
                       SponsorRepository sponsorRepository, PlayerRepository playerRepository,
                       GameRepository gameRepository) {
        this.teamRepository = teamRepository;
        this.stadiumRepository = stadiumRepository;
        this.sponsorRepository = sponsorRepository;
        this.playerRepository = playerRepository;
        this.gameRepository = gameRepository;
    }

    @Transactional(readOnly = true)
    public Page<TeamResponse> findAll(Pageable pageable) {
        return teamRepository.findAll(pageable).map(TeamResponse::from);
    }

    @Transactional(readOnly = true)
    public TeamResponse findById(Long id) {
        return TeamResponse.from(getTeam(id));
    }

    @Transactional(readOnly = true)
    public Page<TeamResponse> search(Conference conference, Division division, String name, Pageable pageable) {
        return teamRepository.search(conference, division, name == null ? "" : name.trim(), pageable)
                .map(TeamResponse::from);
    }

    public TeamResponse create(TeamRequest request) {
        if (teamRepository.existsByNameIgnoreCase(request.name())) {
            throw new ConflictException("Já existe um time com o nome '" + request.name() + "'");
        }
        if (teamRepository.existsByAbbreviation(request.abbreviation())) {
            throw new ConflictException("Já existe um time com a sigla '" + request.abbreviation() + "'");
        }
        Team team = new Team();
        apply(team, request);
        return TeamResponse.from(teamRepository.save(team));
    }

    public TeamResponse update(Long id, TeamRequest request) {
        Team team = getTeam(id);
        if (!team.getName().equalsIgnoreCase(request.name()) && teamRepository.existsByNameIgnoreCase(request.name())) {
            throw new ConflictException("Já existe um time com o nome '" + request.name() + "'");
        }
        if (!team.getAbbreviation().equals(request.abbreviation())
                && teamRepository.existsByAbbreviation(request.abbreviation())) {
            throw new ConflictException("Já existe um time com a sigla '" + request.abbreviation() + "'");
        }
        apply(team, request);
        return TeamResponse.from(team);
    }

    public void delete(Long id) {
        Team team = getTeam(id);
        if (gameRepository.existsByTeam(id)) {
            throw new ConflictException("O time possui partidas cadastradas; remova-as antes de excluir o time");
        }
        // Jogadores viram free agents e os vínculos com patrocinadores são desfeitos
        team.getPlayers().forEach(p -> p.setTeam(null));
        team.getSponsors().clear();
        teamRepository.delete(team);
    }

    @Transactional(readOnly = true)
    public Page<PlayerResponse> findPlayers(Long teamId, Pageable pageable) {
        getTeam(teamId);
        return playerRepository.findByTeamId(teamId, pageable).map(PlayerResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<SponsorResponse> findSponsors(Long teamId, Pageable pageable) {
        getTeam(teamId);
        return sponsorRepository.findByTeamsId(teamId, pageable).map(SponsorResponse::from);
    }

    public TeamResponse addSponsor(Long teamId, Long sponsorId) {
        Team team = getTeam(teamId);
        Sponsor sponsor = sponsorRepository.findById(sponsorId)
                .orElseThrow(() -> new ResourceNotFoundException("Patrocinador", sponsorId));
        if (!team.getSponsors().add(sponsor)) {
            throw new ConflictException("O patrocinador já está vinculado a este time");
        }
        sponsor.getTeams().add(team);
        return TeamResponse.from(team);
    }

    public void removeSponsor(Long teamId, Long sponsorId) {
        Team team = getTeam(teamId);
        Sponsor sponsor = sponsorRepository.findById(sponsorId)
                .orElseThrow(() -> new ResourceNotFoundException("Patrocinador", sponsorId));
        if (!team.getSponsors().remove(sponsor)) {
            throw new ResourceNotFoundException("Vínculo time/patrocinador", sponsorId);
        }
        sponsor.getTeams().remove(team);
    }

    Team getTeam(Long id) {
        return teamRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Time", id));
    }

    private void apply(Team team, TeamRequest r) {
        team.setName(r.name());
        team.setCity(r.city());
        team.setAbbreviation(r.abbreviation());
        team.setConference(r.conference());
        team.setDivision(r.division());
        team.setFoundedYear(r.foundedYear());
        team.setSuperBowlTitles(r.superBowlTitles());

        Long currentStadiumId = team.getStadium() != null ? team.getStadium().getId() : null;
        if (r.stadiumId() == null) {
            team.setStadium(null);
        } else if (!r.stadiumId().equals(currentStadiumId)) {
            Stadium stadium = stadiumRepository.findById(r.stadiumId())
                    .orElseThrow(() -> new ResourceNotFoundException("Estádio", r.stadiumId()));
            if (teamRepository.existsByStadiumId(stadium.getId())) {
                throw new ConflictException("O estádio '" + stadium.getName() + "' já é sede de outro time");
            }
            team.setStadium(stadium);
            stadium.setTeam(team);
        }
    }
}
