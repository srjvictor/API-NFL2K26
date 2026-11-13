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

        chiefsRoster(kc);
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

    /**
     * Elenco ativo do Kansas City Chiefs: 54 jogadores (o long snapper fica de fora, pois a API não tem essa posição).
     * Fonte: página pública de elenco da ESPN, em novembro de 2026. É um retrato daquele dia e muda com a temporada.
     * Colunas: camisa, nome, sobrenome, posição, nascimento, altura (cm), peso (kg), universidade.
     */
    private static final String[][] CHIEFS_ROSTER = {
            {"1", "Xavier", "Worthy", "WR", "2003-04-27", "180", "75", "Texas"},
            {"2", "Josh", "Downs", "WR", "2001-08-12", "175", "78", "North Carolina"},
            {"4", "Rashee", "Rice", "WR", "2000-04-22", "185", "93", "SMU"},
            {"5", "Mansoor", "Delane", "CB", "2003-12-15", "183", "86", "LSU"},
            {"6", "Justin", "Fields", "QB", "1999-03-05", "191", "103", "Ohio State"},
            {"7", "Harrison", "Butker", "K", "1995-07-14", "193", "93", "Georgia Tech"},
            {"8", "Kristian", "Fulton", "CB", "1998-09-03", "180", "89", "LSU"},
            {"9", "Kenneth", "Walker III", "RB", "2000-10-20", "175", "96", "Michigan State"},
            {"10", "Emmett", "Johnson", "RB", "2003-10-10", "180", "91", "Nebraska"},
            {"11", "Jalen", "Royals", "WR", "2003-02-18", "183", "88", "Utah State"},
            {"12", "Jared", "Wiley", "TE", "2000-11-02", "198", "113", "TCU"},
            {"13", "Cyrus", "Allen", "WR", "2003-02-11", "180", "82", "Cincinnati"},
            {"14", "Matt", "Araiza", "P", "2000-05-16", "185", "91", "San Diego State"},
            {"15", "Patrick", "Mahomes", "QB", "1995-09-17", "188", "102", "Texas Tech"},
            {"17", "Alohi", "Gilman", "S", "1997-09-17", "178", "91", "Notre Dame"},
            {"19", "Garrett", "Nussmeier", "QB", "2002-02-06", "185", "93", "LSU"},
            {"20", "Nohl", "Williams", "CB", "2002-09-23", "185", "91", "California"},
            {"21", "Jaden", "Hicks", "S", "2002-08-16", "185", "98", "Washington State"},
            {"22", "Jadon", "Canady", "CB", "2003-05-01", "180", "84", "Oregon"},
            {"23", "Drue", "Tranquill", "LB", "1995-08-15", "188", "106", "Notre Dame"},
            {"27", "Chamarri", "Conner", "S", "2000-07-11", "183", "92", "Virginia Tech"},
            {"29", "Xavier", "Nwankpa", "S", "2003-12-08", "188", "97", "Iowa"},
            {"30", "Chris", "Roland-Wallace", "CB", "2001-11-23", "180", "92", "USC"},
            {"31", "Jeffrey", "Bassa", "LB", "2002-09-20", "188", "107", "Oregon"},
            {"32", "Nick", "Bolton", "LB", "2000-03-10", "180", "108", "Missouri"},
            {"34", "Jaydn", "Ott", "RB", "2002-12-16", "180", "94", "Oklahoma"},
            {"39", "Melvin", "Smith Jr.", "CB", "2002-04-12", "185", "84", "Southern Arkansas"},
            {"43", "Jack", "Cochrane", "LB", "1999-02-09", "191", "107", "South Dakota"},
            {"44", "Cooper", "McDonald", "LB", "2001-08-07", "191", "109", "TCU"},
            {"47", "Ben", "VanSumeren", "RB", "2000-05-05", "188", "113", "Michigan State"},
            {"48", "Cole", "Christiansen", "LB", "1997-07-30", "185", "104", "Army"},
            {"51", "R Mason", "Thomas", "DL", "2004-08-25", "188", "113", "Oklahoma"},
            {"52", "Creed", "Humphrey", "OL", "1999-06-28", "193", "137", "Oklahoma"},
            {"56", "George", "Karlaftis", "DL", "2001-04-03", "193", "119", "Purdue"},
            {"61", "Diego", "Pounds", "OL", "2002-12-12", "198", "150", "Ole Miss"},
            {"64", "Hunter", "Nourzad", "OL", "2000-11-26", "191", "144", "Penn State"},
            {"65", "Trey", "Smith", "OL", "1999-06-16", "198", "146", "Tennessee"},
            {"66", "Mike", "Caliendo", "OL", "1997-10-21", "193", "137", "Western Michigan"},
            {"70", "Kahlil", "Benson", "OL", "2002-08-20", "198", "145", "Indiana"},
            {"73", "Joshua", "Ezeudu", "OL", "1999-09-19", "193", "140", "North Carolina"},
            {"76", "Kingsley", "Suamataia", "OL", "2003-01-18", "193", "148", "BYU"},
            {"77", "Jaylon", "Moore", "OL", "1998-01-09", "193", "141", "Western Michigan"},
            {"83", "Noah", "Gray", "TE", "1999-04-30", "191", "109", "Duke"},
            {"84", "Justin", "Watson", "WR", "1996-04-04", "188", "98", "Pennsylvania"},
            {"85", "Andrew", "Armstrong", "WR", "2000-10-31", "193", "95", "Arkansas"},
            {"87", "Travis", "Kelce", "TE", "1989-10-05", "196", "113", "Cincinnati"},
            {"88", "Jake", "Briningstool", "TE", "2002-12-09", "198", "109", "Clemson"},
            {"91", "Felix", "Anudike-Uzomah", "DL", "2002-01-24", "193", "116", "Kansas State"},
            {"92", "Khyiris", "Tonga", "DL", "1996-07-07", "188", "152", "BYU"},
            {"94", "Bryson", "Eason", "DL", "2002-01-21", "191", "143", "Tennessee"},
            {"95", "Chris", "Jones", "DL", "1994-07-03", "198", "141", "Mississippi State"},
            {"96", "Marcus", "Harris", "DL", "2000-09-27", "191", "134", "Auburn"},
            {"97", "Ashton", "Gillotte", "DL", "2002-10-30", "191", "122", "Louisville"},
            {"99", "Peter", "Woods", "DL", "2005-03-05", "191", "141", "Clemson"},
    };

    private void chiefsRoster(Team kc) {
        for (String[] r : CHIEFS_ROSTER) {
            player(r[1], r[2], Integer.parseInt(r[0]), Position.valueOf(r[3]), r[4],
                    Integer.parseInt(r[5]), Integer.parseInt(r[6]), r[7], kc);
        }
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
