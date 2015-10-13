package com.jtouzy.cv.tools.executors.dayswitch;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import com.jtouzy.cv.model.classes.ChampionshipWeeks;
import com.jtouzy.cv.model.classes.Match;
import com.jtouzy.cv.model.dao.ChampionshipWeeksDAO;
import com.jtouzy.cv.model.dao.MatchDAO;
import com.jtouzy.cv.tools.errors.ToolsException;
import com.jtouzy.cv.tools.model.ParameterNames;
import com.jtouzy.cv.tools.model.ToolExecutorImpl;
import com.jtouzy.dao.DAOManager;
import com.jtouzy.dao.errors.DAOException;

public class DaySwitcher extends ToolExecutorImpl {
	private Integer firstDay;
	private Integer dayToSwitch;
	private Integer championshipId;
	
	public DaySwitcher() {
	}

	@Override
	public void execute() 
	throws ToolsException {
		try {
			if (!hasParameter(ParameterNames.ID))
				throw new ToolsException("Le numéro de championnat est absent");
			if (!hasParameter(ParameterNames.FIRSTDAY))
				throw new ToolsException("La première journée doit être indiquée");			
			if (!hasParameter(ParameterNames.SWITCHDAY))
				throw new ToolsException("La journée d'échange doit être indiquée");
			
			this.championshipId = Integer.parseInt(getParameterValue(ParameterNames.ID));
			this.firstDay = Integer.parseInt(getParameterValue(ParameterNames.FIRSTDAY));
			this.dayToSwitch = Integer.parseInt(getParameterValue(ParameterNames.SWITCHDAY));
			initializeContext();
			switchDay();
		} catch (Exception ex) {
			throw new ToolsException(ex);
		}
	}
	
	private void switchDay() {
		try {
			MatchDAO dao = DAOManager.getDAO(this.connection, MatchDAO.class);
			List<Match> matchs = dao.getAllByChampionship(this.championshipId);
			List<Match> firstDayMatchs = matchs.stream()
					                           .filter(m -> m.getStep().equals(firstDay))
					                           .collect(Collectors.toList());
			List<Match> switchDayMatchs = matchs.stream()
                    							.filter(m -> m.getStep().equals(dayToSwitch))
                								.collect(Collectors.toList());
			List<ChampionshipWeeks> weeks = DAOManager.getDAO(this.connection, ChampionshipWeeksDAO.class)
					                                  .getAllByChampionship(this.championshipId);
			firstDayMatchs.forEach(m -> {
				LocalDateTime date = m.getFirstTeam().getDate();
				m.setStep(dayToSwitch);
				m.setDate(weeks.get(dayToSwitch-1)
						       .getWeekDate()
						       .plusDays(date.getDayOfWeek().getValue() - 1)
						       .plusHours(date.getHour())
						       .plusMinutes(date.getMinute()));
			});
			switchDayMatchs.forEach(m -> {
				LocalDateTime date = m.getFirstTeam().getDate();
				m.setStep(firstDay);
				m.setDate(weeks.get(firstDay-1)
						       .getWeekDate()
						       .plusDays(date.getDayOfWeek().getValue() - 1)
						       .plusHours(date.getHour())
						       .plusMinutes(date.getMinute()));
			});
			firstDayMatchs.addAll(switchDayMatchs);
			
			try {
				this.connection.setAutoCommit(false);
				Iterator<Match> it = firstDayMatchs.iterator();
				Match match;
				while (it.hasNext()) {
					match = it.next();
					dao.update(match);
				}
				this.connection.commit();
				this.connection.setAutoCommit(true);
			} catch (SQLException ex) {
				try {
					if (!this.connection.getAutoCommit()) {
						this.connection.rollback();
						this.connection.setAutoCommit(true);
					}
				} catch (SQLException ex2) {
					throw new ToolsException(ex2);
				}
				throw new ToolsException(ex);
			}
			
		} catch (DAOException ex) {
			throw new ToolsException(ex);
		}
	}
}
