package com.jtouzy.cv.tools.executors.updinf;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.jtouzy.cv.model.classes.ChampionshipWeeks;
import com.jtouzy.cv.model.classes.Gym;
import com.jtouzy.cv.model.classes.Match;
import com.jtouzy.cv.model.classes.SeasonTeam;
import com.jtouzy.cv.model.dao.ChampionshipWeeksDAO;
import com.jtouzy.cv.model.dao.GymDAO;
import com.jtouzy.cv.model.dao.MatchDAO;
import com.jtouzy.cv.model.dao.SeasonDAO;
import com.jtouzy.cv.model.dao.SeasonTeamDAO;
import com.jtouzy.cv.tools.errors.ToolsException;
import com.jtouzy.cv.tools.model.ParameterNames;
import com.jtouzy.cv.tools.model.ToolExecutorImpl;
import com.jtouzy.dao.errors.DAOCrudException;
import com.jtouzy.dao.errors.QueryException;
import com.jtouzy.dao.errors.validation.DataValidationException;

public class UpdateTeamInformations extends ToolExecutorImpl {
	private SeasonTeam seasonTeam;
	private static final Logger logger = LogManager.getLogger(UpdateTeamInformations.class);
	
	@Override
	public void execute() {
		if (!hasParameter(ParameterNames.ID))
			throw new ToolsException("Numéro d'équipe absent");
		if (!hasParameter(ParameterNames.DATE) && !hasParameter(ParameterNames.GYM))
			throw new ToolsException("Aucune donnée à modifier");
		
		try {
			initializeContext();
			this.connection.setAutoCommit(false);
			updateTeamInfos();
			updateCascade();
			this.connection.commit();
			this.connection.setAutoCommit(true);
		} catch (ToolsException | SQLException ex) {
			try {
				if (!this.connection.getAutoCommit()) {
					this.connection.rollback();
					this.connection.setAutoCommit(true);
				}
			} catch (SQLException ex2) {
				throw new ToolsException(ex);
			}
			throw new ToolsException(ex);
		}
	}
	
	public void updateTeamInfos() {
		try {
			SeasonTeamDAO dao = getDAO(SeasonTeamDAO.class);
			SeasonTeam st = dao.getOne(Integer.parseInt(getParameterValue(ParameterNames.ID)));
			if (st == null)
				throw new ToolsException("Equipe " + getParameterValue(ParameterNames.ID) + " inexistante");
			logger.trace("Mise à jour des informations de l'équipe...");
			if (hasParameter(ParameterNames.DATE)) {
				logger.trace("Mise à jour de la date");
				st.setDate(LocalDateTime.parse(getParameterValue(ParameterNames.DATE), DateTimeFormatter.ofPattern("yyyy-MM-dd,HH:mm")));
			}
			if (hasParameter(ParameterNames.GYM)) {
				Gym gym = getDAO(GymDAO.class).getOne(Integer.parseInt(getParameterValue(ParameterNames.GYM)));
				if (gym == null)
					throw new ToolsException("Gymnase " + getParameterValue(ParameterNames.GYM) + " inexistant");
				logger.trace("Mise à jour du gymnase");
				st.setGym(gym);
			}
			seasonTeam = dao.update(st);
		} catch (QueryException | DAOCrudException | DataValidationException ex) {
			throw new ToolsException(ex);
		}
	}
	
	public void updateCascade() {
		if (!hasParameter(ParameterNames.DATE))
			return;
		try {
			List<Match> allMatchs = 
					getDAO(MatchDAO.class).getAllBySeasonAndUser(
							getDAO(SeasonDAO.class).getCurrentSeason().getIdentifier(), null)
								.stream()
								.filter(m -> {
									return m.getState() == Match.State.C && 
										   seasonTeam.getIdentifier().equals(m.getFirstTeam().getIdentifier()); 
								})
								.collect(Collectors.toList());
			
			List<Integer> championshipIds = allMatchs.stream()
					                                 .map(m -> m.getChampionship().getIdentifier())
					                                 .collect(Collectors.toList());
			Iterator<Integer> it = championshipIds.iterator();
			Integer championshipId;
			HashMap<Integer,List<ChampionshipWeeks>> weeksByChampionships = new HashMap<>();
			while (it.hasNext()) {
				championshipId = it.next();
				if (weeksByChampionships.containsKey(championshipId))
					continue;
				weeksByChampionships.put(championshipId, getDAO(ChampionshipWeeksDAO.class)
						.getAllByChampionship(championshipId));
			}
			
			Iterator<Match> itm = allMatchs.iterator();
			Match match;
			LocalDateTime date = seasonTeam.getDate();
			while (itm.hasNext()) {
				match = itm.next();
				logger.trace("Mise à jour du match n°" + match.getIdentifier());
				match.setDate(weeksByChampionships.get(match.getChampionship().getIdentifier())
						                          .get(match.getStep() - 1)
					                          	  .getWeekDate()
					                          	  .plusDays(date.getDayOfWeek().getValue() - 1)
					                          	  .plusHours(date.getHour())
					                          	  .plusMinutes(date.getMinute()));
				getDAO(MatchDAO.class).update(match);
			}
		} catch (QueryException | DataValidationException | DAOCrudException ex) {
			throw new ToolsException(ex);
		}		
	}
}
