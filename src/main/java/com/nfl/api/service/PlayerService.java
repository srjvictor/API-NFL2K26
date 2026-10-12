package com.nfl.api.service;

import com.nfl.api.domain.Player;
import com.nfl.api.domain.Team;
import com.nfl.api.domain.enums.Position;
import com.nfl.api.dto.PlayerDtos.PlayerRequest;
import com.nfl.api.dto.PlayerDtos.PlayerResponse;
import com.nfl.api.dto.PlayerDtos.PlayerResponseV2;
import com.nfl.api.exception.ConflictException;
import com.nfl.api.exception.ResourceNotFoundException;
import com.nfl.api.repository.PlayerRepository;
import com.nfl.api.repository.TeamRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PlayerService {

    private final PlayerRepository playerRepository;
    private final TeamRepository teamRepository;

    public PlayerService(PlayerRepository playerRepository, TeamRepository teamRepository) {
        this.playerRepository = playerRepository;
        this.teamRepository = teamRepository;
    }

    @Transactional(readOnly = true)
    public Page<PlayerResponse> findAll(Pageable pageable) {
        return playerRepository.findAll(pageable).map(PlayerResponse::from);
    }

    @Transactional(readOnly = true)
    public PlayerResponse findById(Long id) {
        return PlayerResponse.from(getPlayer(id));
    }

    @Transactional(readOnly = true)
    public PlayerResponseV2 findByIdV2(Long id) {
        return PlayerResponseV2.from(getPlayer(id));
    }

    @Transactional(readOnly = true)
    public Page<PlayerResponse> search(Position position, Long teamId, String name, Pageable pageable) {
        return playerRepository.search(position, teamId, name == null ? "" : name.trim(), pageable)
                .map(PlayerResponse::from);
    }

    public PlayerResponse create(PlayerRequest request) {
        if (request.teamId() != null
                && playerRepository.existsByTeamIdAndJerseyNumber(request.teamId(), request.jerseyNumber())) {
            throw new ConflictException("A camisa " + request.jerseyNumber() + " já está em uso neste time");
        }
        Player player = new Player();
        apply(player, request);
        return PlayerResponse.from(playerRepository.save(player));
    }

    public PlayerResponse update(Long id, PlayerRequest request) {
        Player player = getPlayer(id);
        if (request.teamId() != null && playerRepository
                .existsByTeamIdAndJerseyNumberAndIdNot(request.teamId(), request.jerseyNumber(), id)) {
            throw new ConflictException("A camisa " + request.jerseyNumber() + " já está em uso neste time");
        }
        apply(player, request);
        return PlayerResponse.from(player);
    }

    public void delete(Long id) {
        playerRepository.delete(getPlayer(id));
    }

    private Player getPlayer(Long id) {
        return playerRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Jogador", id));
    }

    private void apply(Player p, PlayerRequest r) {
        p.setFirstName(r.firstName());
        p.setLastName(r.lastName());
        p.setJerseyNumber(r.jerseyNumber());
        p.setPosition(r.position());
        p.setBirthDate(r.birthDate());
        p.setHeightCm(r.heightCm());
        p.setWeightKg(r.weightKg());
        p.setCollege(r.college());
        Team team = null;
        if (r.teamId() != null) {
            team = teamRepository.findById(r.teamId())
                    .orElseThrow(() -> new ResourceNotFoundException("Time", r.teamId()));
        }
        p.setTeam(team);
    }
}
