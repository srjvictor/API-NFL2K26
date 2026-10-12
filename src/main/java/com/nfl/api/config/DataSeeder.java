package com.nfl.api.config;

import com.nfl.api.domain.*;
import com.nfl.api.domain.enums.*;
import com.nfl.api.repository.*;
import com.nfl.api.service.ApiKeyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Popula o banco H2 com dados de exemplo e cria a chave ADMIN de desenvolvimento. */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final StadiumRepository stadiums;
    private final TeamRepository teams;
    private final PlayerRepository players;
    private final SponsorRepository sponsors;
    private final GameRepository games;
    private final ApiKeyService apiKeyService;
    private final AppProperties properties;

    public DataSeeder(StadiumRepository stadiums, TeamRepository teams, PlayerRepository players,
                      SponsorRepository sponsors, GameRepository games, ApiKeyService apiKeyService,
                      AppProperties properties) {
        this.stadiums = stadiums;
        this.teams = teams;
        this.players = players;
        this.sponsors = sponsors;
        this.games = games;
        this.apiKeyService = apiKeyService;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(String... args) {
        String adminKey = properties.security().adminKey();
        apiKeyService.registerIfAbsent(adminKey, "Administrador (seed)", ApiKeyRole.ADMIN);
        // Só o início da chave vai para o log, para não expor o segredo em produção
        log.info("Chave ADMIN registrada (header X-API-Key), prefixo: {}...",
                adminKey.substring(0, Math.min(8, adminKey.length())));

        if (teams.count() > 0) {
            return;
        }

        Stadium arrowhead = stadium("GEHA Field at Arrowhead Stadium", "Kansas City", 76416, RoofType.OPEN, 1972);
        Stadium linc = stadium("Lincoln Financial Field", "Philadelphia", 69796, RoofType.OPEN, 2003);
        Stadium levis = stadium("Levi's Stadium", "Santa Clara", 68500, RoofType.OPEN, 2014);
        Stadium att = stadium("AT&T Stadium", "Arlington", 80000, RoofType.RETRACTABLE, 2009);
        Stadium highmark = stadium("Highmark Stadium", "Orchard Park", 62000, RoofType.OPEN, 2026);
        stadium("Allegiant Stadium", "Las Vegas", 65000, RoofType.DOME, 2020);

        Team kc = team("Kansas City Chiefs", "Kansas City", "KC", Conference.AFC, Division.WEST, 1960, 4, arrowhead);
        Team phi = team("Philadelphia Eagles", "Philadelphia", "PHI", Conference.NFC, Division.EAST, 1933, 2, linc);
        Team sf = team("San Francisco 49ers", "San Francisco", "SF", Conference.NFC, Division.WEST, 1946, 5, levis);
        Team dal = team("Dallas Cowboys", "Dallas", "DAL", Conference.NFC, Division.EAST, 1960, 5, att);
        Team buf = team("Buffalo Bills", "Buffalo", "BUF", Conference.AFC, Division.EAST, 1960, 0, highmark);

        player("Patrick", "Mahomes", 15, Position.QB, "1995-09-17", 188, 102, "Texas Tech", kc);
        player("Travis", "Kelce", 87, Position.TE, "1989-10-05", 196, 113, "Cincinnati", kc);
        player("Jalen", "Hurts", 1, Position.QB, "1998-08-07", 185, 101, "Oklahoma", phi);
        player("Saquon", "Barkley", 26, Position.RB, "1997-02-09", 183, 106, "Penn State", phi);
        player("Brock", "Purdy", 13, Position.QB, "1999-12-27", 185, 99, "Iowa State", sf);
        player("Christian", "McCaffrey", 23, Position.RB, "1996-06-07", 180, 95, "Stanford", sf);
        player("Dak", "Prescott", 4, Position.QB, "1993-07-29", 188, 108, "Mississippi State", dal);
        player("CeeDee", "Lamb", 88, Position.WR, "1999-04-08", 188, 91, "Oklahoma", dal);
        player("Josh", "Allen", 17, Position.QB, "1996-05-21", 196, 107, "Wyoming", buf);
        player("Aaron", "Rodgers", 8, Position.QB, "1983-12-02", 188, 102, "California", null);

        Sponsor nike = sponsor("Nike", "Material esportivo", "220000000.00", "https://www.nike.com");
        Sponsor gatorade = sponsor("Gatorade", "Bebidas", "50000000.00", "https://www.gatorade.com");
        Sponsor verizon = sponsor("Verizon", "Telecomunicações", "150000000.00", "https://www.verizon.com");
        Sponsor budLight = sponsor("Bud Light", "Bebidas", "230000000.00", "https://www.budlight.com");
        Sponsor microsoft = sponsor("Microsoft", "Tecnologia", "100000000.00", "https://www.microsoft.com");

        link(kc, nike, gatorade);
        link(phi, nike, verizon);
        link(sf, nike, microsoft);
        link(dal, verizon, budLight);
        link(buf, gatorade);

        game(2025, 1, "2025-09-04T20:20:00", phi, dal, linc, 24, 20, GameStatus.FINAL);
        game(2025, 9, "2025-11-02T16:25:00", buf, kc, highmark, 28, 21, GameStatus.FINAL);
        game(2026, 7, "2026-10-18T16:25:00", kc, phi, arrowhead, null, null, GameStatus.SCHEDULED);
        game(2026, 8, "2026-10-25T20:20:00", sf, dal, levis, null, null, GameStatus.SCHEDULED);

        log.info("Dados de exemplo carregados: {} times, {} jogadores, {} estádios, {} patrocinadores, {} partidas",
                teams.count(), players.count(), stadiums.count(), sponsors.count(), games.count());
    }

    private Stadium stadium(String name, String city, int capacity, RoofType roof, int opened) {
        Stadium s = new Stadium();
        s.setName(name);
        s.setCity(city);
        s.setCapacity(capacity);
        s.setRoofType(roof);
        s.setOpenedYear(opened);
        return stadiums.save(s);
    }

    private Team team(String name, String city, String abbr, Conference conf, Division div, int founded,
                      int titles, Stadium stadium) {
        Team t = new Team();
        t.setName(name);
        t.setCity(city);
        t.setAbbreviation(abbr);
        t.setConference(conf);
        t.setDivision(div);
        t.setFoundedYear(founded);
        t.setSuperBowlTitles(titles);
        t.setStadium(stadium);
        stadium.setTeam(t);
        return teams.save(t);
    }

    private void player(String first, String last, int number, Position pos, String birth, int height,
                        int weight, String college, Team team) {
        Player p = new Player();
        p.setFirstName(first);
        p.setLastName(last);
        p.setJerseyNumber(number);
        p.setPosition(pos);
        p.setBirthDate(LocalDate.parse(birth));
        p.setHeightCm(height);
        p.setWeightKg(weight);
        p.setCollege(college);
        p.setTeam(team);
        if (team != null) {
            team.getPlayers().add(p);
        }
        players.save(p);
    }

    private Sponsor sponsor(String name, String industry, String value, String website) {
        Sponsor s = new Sponsor();
        s.setName(name);
        s.setIndustry(industry);
        s.setAnnualValueUsd(new BigDecimal(value));
        s.setWebsite(website);
        return sponsors.save(s);
    }

    private void link(Team team, Sponsor... list) {
        for (Sponsor s : list) {
            team.getSponsors().add(s);
            s.getTeams().add(team);
        }
    }

    private void game(int season, int week, String kickoff, Team home, Team away, Stadium stadium,
                      Integer homeScore, Integer awayScore, GameStatus status) {
        Game g = new Game();
        g.setSeason(season);
        g.setWeek(week);
        g.setKickoff(LocalDateTime.parse(kickoff));
        g.setHomeTeam(home);
        g.setAwayTeam(away);
        g.setStadium(stadium);
        g.setHomeScore(homeScore);
        g.setAwayScore(awayScore);
        g.setStatus(status);
        games.save(g);
    }
}
