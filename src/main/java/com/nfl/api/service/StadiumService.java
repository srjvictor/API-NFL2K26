package com.nfl.api.service;

import com.nfl.api.domain.Stadium;
import com.nfl.api.domain.enums.RoofType;
import com.nfl.api.dto.GameDtos.GameResponse;
import com.nfl.api.dto.StadiumDtos.StadiumRequest;
import com.nfl.api.dto.StadiumDtos.StadiumResponse;
import com.nfl.api.exception.ConflictException;
import com.nfl.api.exception.ResourceNotFoundException;
import com.nfl.api.repository.GameRepository;
import com.nfl.api.repository.StadiumRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class StadiumService {

    private final StadiumRepository stadiumRepository;
    private final GameRepository gameRepository;

    public StadiumService(StadiumRepository stadiumRepository, GameRepository gameRepository) {
        this.stadiumRepository = stadiumRepository;
        this.gameRepository = gameRepository;
    }

    @Transactional(readOnly = true)
    public Page<StadiumResponse> findAll(Pageable pageable) {
        return stadiumRepository.findAll(pageable).map(StadiumResponse::from);
    }

    @Transactional(readOnly = true)
    public StadiumResponse findById(Long id) {
        return StadiumResponse.from(getStadium(id));
    }

    @Transactional(readOnly = true)
    public Page<StadiumResponse> search(int minCapacity, RoofType roofType, Pageable pageable) {
        return stadiumRepository.search(minCapacity, roofType, pageable).map(StadiumResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<GameResponse> findGames(Long stadiumId, Pageable pageable) {
        getStadium(stadiumId);
        return gameRepository.findByStadiumId(stadiumId, pageable).map(GameResponse::from);
    }

    public StadiumResponse create(StadiumRequest request) {
        if (stadiumRepository.existsByNameIgnoreCase(request.name())) {
            throw new ConflictException("Já existe um estádio com o nome '" + request.name() + "'");
        }
        Stadium stadium = new Stadium();
        apply(stadium, request);
        return StadiumResponse.from(stadiumRepository.save(stadium));
    }

    public StadiumResponse update(Long id, StadiumRequest request) {
        Stadium stadium = getStadium(id);
        if (!stadium.getName().equalsIgnoreCase(request.name())
                && stadiumRepository.existsByNameIgnoreCase(request.name())) {
            throw new ConflictException("Já existe um estádio com o nome '" + request.name() + "'");
        }
        apply(stadium, request);
        return StadiumResponse.from(stadium);
    }

    public void delete(Long id) {
        Stadium stadium = getStadium(id);
        if (stadium.getTeam() != null) {
            throw new ConflictException("O estádio é sede do time '" + stadium.getTeam().getName()
                    + "'; desvincule-o antes de excluir");
        }
        if (gameRepository.existsByStadiumId(id)) {
            throw new ConflictException("O estádio possui partidas cadastradas");
        }
        stadiumRepository.delete(stadium);
    }

    private Stadium getStadium(Long id) {
        return stadiumRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Estádio", id));
    }

    private void apply(Stadium s, StadiumRequest r) {
        s.setName(r.name());
        s.setCity(r.city());
        s.setCapacity(r.capacity());
        s.setRoofType(r.roofType());
        s.setOpenedYear(r.openedYear());
    }
}
