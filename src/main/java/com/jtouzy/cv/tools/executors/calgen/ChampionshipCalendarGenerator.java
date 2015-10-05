package com.jtouzy.cv.tools.executors.calgen;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.jtouzy.cv.model.classes.Championship;
import com.jtouzy.cv.model.classes.ChampionshipTeam;
import com.jtouzy.cv.model.classes.ChampionshipWeeks;
import com.jtouzy.cv.model.classes.Match;
import com.jtouzy.cv.model.classes.Season;
import com.jtouzy.cv.model.classes.SeasonTeam;
import com.jtouzy.cv.model.dao.ChampionshipDAO;
import com.jtouzy.cv.model.dao.ChampionshipWeeksDAO;
import com.jtouzy.cv.model.dao.MatchDAO;
import com.jtouzy.cv.model.dao.SeasonTeamDAO;
import com.jtouzy.cv.model.errors.CalendarGenerationException;
import com.jtouzy.cv.tools.errors.ToolsException;
import com.jtouzy.cv.tools.model.ParameterNames;
import com.jtouzy.cv.tools.model.ToolExecutorImpl;
import com.jtouzy.dao.DAOManager;
import com.jtouzy.dao.errors.DAOCrudException;
import com.jtouzy.dao.errors.DAOInstantiationException;
import com.jtouzy.dao.errors.QueryException;
import com.jtouzy.dao.errors.validation.DataValidationException;

public class ChampionshipCalendarGenerator extends ToolExecutorImpl {
	private Integer championshipId;
	private Season season;
	private Championship championship;
	private List<ChampionshipTeam> teams;
	private List<ChampionshipWeeks> weeks;
	private Integer teamCount;
	private Integer dayCount;
	private Integer realDayCount;
	private boolean returnMatchs;
	private boolean simulation;
	private List<Match> matchs;
	private List<SeasonTeam> seasonTeams;
	
	public ChampionshipCalendarGenerator() {
	}
	
	@Override
	public void execute() {
		try {
			if (!hasParameter(ParameterNames.ID)) {
				throw new ToolsException("L'identifiant du championnat doit être renseigné");
			}
			boolean returnMatchs = false;
			if (hasParameter(ParameterNames.RETURN)) {
				returnMatchs = true;
			}
			String championshipID = getParameterValue(ParameterNames.ID);
			initializeContext();
			this.championshipId = Integer.parseInt(championshipID);
			this.returnMatchs = returnMatchs;
			this.simulation = hasParameter(ParameterNames.SIMULATION);
			generate();
		} catch (Exception ex) {
			throw new ToolsException(ex);
		}
	}
	
	public void generate()
	throws CalendarGenerationException, DAOCrudException, DataValidationException, DAOInstantiationException {
		try {
			if (!simulation)
				connection.setAutoCommit(false);
			buildCalendar();
			if (!simulation) {
				MatchDAO dao = DAOManager.getDAO(connection, MatchDAO.class);
				dao.create(matchs);
				connection.commit();
				connection.setAutoCommit(true);
			}
		} catch (DAOCrudException | DataValidationException | DAOInstantiationException | SQLException ex) {
			try {
				if (!connection.getAutoCommit()) {
					connection.rollback();
					connection.setAutoCommit(true);
				}
			} catch (SQLException ex2) {
				throw new CalendarGenerationException(ex);
			}
			throw new CalendarGenerationException(ex);
		}
	}
	
	private boolean isExemptMatch(ChampionshipTeam team1, ChampionshipTeam team2) {
		return team1.getTeam().getLabel().equals("Exempt") ||
			   team2.getTeam().getLabel().equals("Exempt");
	}
	
	public List<Match> getMatchs() {
		return this.matchs;
	}
	
	public List<Match> buildCalendar()
	throws CalendarGenerationException {
		// -> Recherche des infos championnat + équipes pour le championnat + saison associée
		findChampionshipMainData();
		// -> Contrôle nombre d'équipes + ajout exempt si nécessaire
		controlAndResolveTeams();
		
		// -> Informations
		this.teamCount = this.teams.size();
		this.dayCount  = this.teamCount - 1;
		this.realDayCount = this.returnMatchs ? this.dayCount*2 : this.dayCount;

		// -> Recherche des semaines associées au championnat
		findChampionshipWeeks();
		// -> Contrôle nombre de semaines
		controlWeeks();
		
		// -> Recherche des équipes/saison pour avoir les informations sur les dates
		findSeasonTeams();
		// -> Contrôle des équipes/saison
		controlSeasonTeams();
		
		// -> Génération des matchs
		calculateCalendar();
		return this.matchs;
	}
	
	private void findChampionshipMainData()
	throws CalendarGenerationException {
		try {
			ChampionshipDAO championshipDao = DAOManager.getDAO(this.connection, ChampionshipDAO.class);
			this.championship = championshipDao.getOneWithTeams(championshipId);
			if (this.championship == null) {
				throw new CalendarGenerationException("Championnat " + championshipId + " inexistant");
			}
			this.teams = this.championship.getTeams();
			this.season = this.championship.getCompetition().getSeason();
			this.season = championshipDao.getOneWithDetails(championshipId).getCompetition().getSeason();
		} catch (DAOInstantiationException | QueryException ex) {
			throw new CalendarGenerationException(ex);
		}
	}
	
	private void controlAndResolveTeams()
	throws CalendarGenerationException {
		int teamCount = this.teams.size();
		if (teamCount == 0) {
			throw new CalendarGenerationException("Aucune équipe dans le championnat");
		}
		if (teamCount%2 != 0) {
			Championship chp = new Championship();
			chp.setIdentifier(this.championshipId);
			SeasonTeam exempt = new SeasonTeam();
			exempt.setLabel("Exempt");
			ChampionshipTeam ctExempt = new ChampionshipTeam();
			ctExempt.setChampionship(chp);
			ctExempt.setTeam(exempt);
			ctExempt.setBonus(0);
			ctExempt.setForfeit(0);
			ctExempt.setLoose(0);
			ctExempt.setLoose3By2(0);
			ctExempt.setPlay(0);
			ctExempt.setPoints(0);
			ctExempt.setPointsAgainst(0);
			ctExempt.setPointsFor(0);
			ctExempt.setSetsAgainst(0);
			ctExempt.setSetsFor(0);
			ctExempt.setWin(0);
			this.teams.add(ctExempt);
		}
	}
	
	private void findChampionshipWeeks()
	throws CalendarGenerationException {
		try {
			ChampionshipWeeksDAO championshipDao = DAOManager.getDAO(this.connection, ChampionshipWeeksDAO.class);	
			this.weeks = championshipDao.getAllByChampionship(championshipId);
		} catch (DAOInstantiationException | QueryException ex) {
			throw new CalendarGenerationException(ex);
		} 
	}
	
	private void controlWeeks()
	throws CalendarGenerationException {
		int weeksCount = this.weeks.size();
		if (weeksCount == 0) {
			throw new CalendarGenerationException("Aucune semaine définie pour la génération");
		}
		if (weeksCount != this.realDayCount) {
			throw new CalendarGenerationException("Le nombre de semaines enregistré n'est pas cohérent : Pour " + teamCount + " équipes (EXEMPT compris), il faut " + realDayCount + " journées. " + weeksCount + " enregistrées actuellement.");
		}
	}
	
	private void calculateCalendar()
	throws CalendarGenerationException {
		// -> Initialisations
		int matchCount = teamCount/2;
		this.matchs = new ArrayList<>();
		int firstTeamIndex, secondTeamIndex;
		ChampionshipTeam team1, team2;
		Match match;
		SeasonTeam seasonTeam;
		// -> Boucle sur les journées
		for (int j = 1; j <= dayCount; j++) {
			firstTeamIndex = 1;
			secondTeamIndex = teamCount-j+1;
			// -> Boucle sur les matchs pour cette journée
			for (int m = 1; m <= matchCount; m++) {
				team1 = j%2 == 0 ? teams.get(firstTeamIndex-1) : teams.get(secondTeamIndex-1);
				team2 = j%2 == 0 ? teams.get(secondTeamIndex-1) : teams.get(firstTeamIndex-1);
				if (!isExemptMatch(team1, team2)) {
					match = createMatch();
					match.setFirstTeam(team1.getTeam());
					match.setSecondTeam(team2.getTeam());
					match.setStep(j);
					seasonTeam = match.getFirstTeam();
					match.setDate(weeks.get(j-1) 
							           .getWeekDate()
							           .plusDays(seasonTeam.getDate().getDayOfWeek().getValue() - 1)
							           .plusHours(seasonTeam.getDate().getHour())
							           .plusMinutes(seasonTeam.getDate().getMinute()));
					this.matchs.add(match);
				}
				if (j == 1) {
					firstTeamIndex++;
					secondTeamIndex--;
				} else {
					if (firstTeamIndex == 1 || firstTeamIndex == teamCount)
						firstTeamIndex = firstTeamIndex == 1 ? (teamCount - j + 2) : 2;
					else if (firstTeamIndex != teamCount)
						firstTeamIndex++;
					
					secondTeamIndex--;
					if (secondTeamIndex <= (matchCount - j) || secondTeamIndex == 1)
						secondTeamIndex = teamCount;
				}
				
			}
		}
		// -> Génération des matchs retour si nécessaire
		calculateReturnMatchsIfNeeded();
	}
	
	private void calculateReturnMatchsIfNeeded() {
		if (!this.returnMatchs)
			return;
		
		final List<Match> newMatchs = new ArrayList<>();
		Iterator<Match> it = matchs.iterator();
		Match match, match2;
		SeasonTeam seasonTeam;
		int j;
		while (it.hasNext()) {
			match = it.next();
			j = match.getStep() + dayCount;
			match2 = createMatch();
			match2.setFirstTeam(match.getSecondTeam());
			match2.setSecondTeam(match.getFirstTeam());
			match2.setStep(j);
			seasonTeam = match.getFirstTeam();
			match2.setDate(weeks.get(j-1)
					            .getWeekDate()
					            .plusDays(seasonTeam.getDate().getDayOfWeek().getValue() - 1)
					            .plusHours(seasonTeam.getDate().getHour())
					            .plusMinutes(seasonTeam.getDate().getMinute()));
			newMatchs.add(match2);
		}
		this.matchs.addAll(newMatchs);
	}
	
	private Match createMatch() {
		Match match = new Match();
		match.setChampionship(this.championship);
		match.setForfeit(false);
		match.setState(Match.State.C);
		return match;
	}
	
	private void findSeasonTeams()
	throws CalendarGenerationException {
		try {
			this.seasonTeams = DAOManager.getDAO(this.connection, SeasonTeamDAO.class).getAllBySeason(season.getIdentifier());
		} catch (DAOInstantiationException | QueryException ex) {
			throw new CalendarGenerationException(ex);
		}
	}
	
	private void controlSeasonTeams()
	throws CalendarGenerationException {
		if (this.seasonTeams.size() == 0)
			throw new CalendarGenerationException("Aucune équipe enregistrée pour la saison");
	}
}
