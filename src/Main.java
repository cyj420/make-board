import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

class Main extends Thread {
	public static boolean workStarted = false;

	@Override
	public void start() {
		new Thread(() -> {
			while (true) {
				if (workStarted) {
					Factory.getBuildService().buildSite();
				}
				try {
					Thread.sleep(10000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}).start();
	}

	public static void main(String[] args) {
		App app = new App();
		Thread t = new Main();
		t.start();
		app.start();
		t.interrupt();
	}
}

// Session
// 현재 사용자가 이용중인 정보
// 이 안의 정보는 사용자가 프로그램을 사용할 때 동안은 계속 유지된다.
class Session {
	private Member loginedMember;
	private Board currentBoard;

	public Member getLoginedMember() {
		return loginedMember;
	}

	public void setLoginedMember(Member loginedMember) {
		this.loginedMember = loginedMember;
	}

	public Board getCurrentBoard() {
		return currentBoard;
	}

	public void setCurrentBoard(Board currentBoard) {
		this.currentBoard = currentBoard;
	}

	public boolean isLogined() {
		return loginedMember != null;
	}
}

// DB 커넥션(진짜 DB와의 연결을 담당)
class DBConnection {
	DBConnection() {
		connect();
		String sql1 = "CREATE TABLE IF NOT EXISTS article(id INT(10) UNSIGNED NOT NULL PRIMARY KEY AUTO_INCREMENT, "
				+ "regDate DATETIME NOT NULL, " + "title CHAR(100) NOT NULL, " + "`body` TEXT NOT NULL, "
				+ "memberId INT(10) UNSIGNED NOT NULL, " + "boardId INT(10) UNSIGNED NOT NULL)";
		String sql2 = "CREATE TABLE IF NOT EXISTS board(" + "id INT(10) UNSIGNED NOT NULL PRIMARY KEY AUTO_INCREMENT, "
				+ "regDate DATETIME NOT NULL, " + "`name` CHAR(100) NOT NULL," + "`code` CHAR(100) NOT NULL)";

		// SQL을 적는 문서파일
		Statement statement = null;
		try {
			statement = connection.createStatement();
			statement.executeUpdate(sql1);
			statement.executeUpdate(sql2);
		} catch (SQLException e) {
			System.err.printf("[CREATE TABLE 쿼리 오류]\n" + e.getStackTrace() + "\n");
		}

		try {
			if (statement != null) {
				statement.close();
			}
		} catch (SQLException e) {
			System.err.println("[종료 오류]\n" + e.getStackTrace());
		}
	}

	private Connection connection;

	public void connect() {
		String url = "jdbc:mysql://localhost:3306/site5?serverTimezone=UTC";
		String user = "sbsst";
		String password = "sbs123414";
//		String user = "root";
//		String password = "";
		String driverName = "com.mysql.cj.jdbc.Driver";

		try {
			// ① 로드(카카오 택시에 `com.mysql.cj.jdbc.Driver` 라는 실제 택시 드라이버를 등록)
			// 하지만 개발자는 실제로 `com.mysql.cj.jdbc.Driver`를 다룰 일은 없다.
			// 내부적으로 JDBC가 알아서 다 해주기 때문에 우리는 JDBC의 DriverManager 를 통해서 DB와의 연결을 얻으면 된다.
			Class.forName(driverName);

			// ② 연결
			connection = DriverManager.getConnection(url, user, password);
		} catch (ClassNotFoundException e) {
			// `com.mysql.cj.jdbc.Driver` 라는 클래스가 라이브러리로 추가되지 않았다면 오류발생
			System.out.println("[로드 오류]\n" + e.getStackTrace());
		} catch (SQLException e) {
			// DB접속정보가 틀렸다면 오류발생
			System.out.println("[연결 오류]\n" + e.getStackTrace());
		}
	}

	public int selectRowIntValue(String sql) {
		Map<String, Object> row = selectRow(sql);

		for (String key : row.keySet()) {
			Object value = row.get(key);

			if (value instanceof String) {
				return Integer.parseInt((String) value);
			}
			if (value instanceof Long) {
				return (int) (long) value;
			} else {
				return (int) value;
			}
		}

		return -1;
	}

	public String selectRowStringValue(String sql) {
		Map<String, Object> row = selectRow(sql);

		for (String key : row.keySet()) {
			Object value = row.get(key);

			return value + "";
		}

		return "";
	}

	public boolean selectRowBooleanValue(String sql) {
		int rs = selectRowIntValue(sql);

		return rs == 1;
	}

	public Map<String, Object> selectRow(String sql) {
		List<Map<String, Object>> rows = selectRows(sql);

		if (rows.size() > 0) {
			return rows.get(0);
		}

		return new HashMap<>();
	}

	public List<Map<String, Object>> selectRows(String sql) {
		// SQL을 적는 문서파일
		Statement statement = null;
		ResultSet rs = null;

		List<Map<String, Object>> rows = new ArrayList<>();

		try {
			statement = connection.createStatement();
			rs = statement.executeQuery(sql);
			// ResultSet 의 MetaData를 가져온다.
			ResultSetMetaData metaData = rs.getMetaData();
			// ResultSet 의 Column의 갯수를 가져온다.
			int columnSize = metaData.getColumnCount();

			// rs의 내용을 돌려준다.
			while (rs.next()) {
				// 내부에서 map을 초기화
				Map<String, Object> row = new HashMap<>();

				for (int columnIndex = 0; columnIndex < columnSize; columnIndex++) {
					String columnName = metaData.getColumnName(columnIndex + 1);
					// map에 값을 입력 map.put(columnName, columnName으로 getString)
					row.put(columnName, rs.getObject(columnName));
				}
				// list에 저장
				rows.add(row);
			}
		} catch (SQLException e) {
			System.err.printf("[SELECT 쿼리 오류, %s]\n" + e.getStackTrace() + "\n", sql);
		}

		try {
			if (statement != null) {
				statement.close();
			}

			if (rs != null) {
				rs.close();
			}
		} catch (SQLException e) {
			System.err.println("[SELECT 종료 오류]\n" + e.getStackTrace());
		}

		return rows;
	}

	public int update(String sql) {
		// UPDATE 명령으로 몇개의 데이터가 수정되었는지
		int affectedRows = 0;

		// SQL을 적는 문서파일
		Statement statement = null;

		try {
			statement = connection.createStatement();
			affectedRows = statement.executeUpdate(sql);
		} catch (SQLException e) {
			System.err.printf("[UPDATE 쿼리 오류, %s]\n" + e.getStackTrace() + "\n", sql);
		}

		try {
			if (statement != null) {
				statement.close();
			}
		} catch (SQLException e) {
			System.err.println("[UPDATE 종료 오류]\n" + e.getStackTrace());
		}

		return affectedRows;
	}

	public int insert(String sql) {
		int id = -1;

		// SQL을 적는 문서파일
		Statement statement = null;
		// SQL의 실행결과 보고서
		ResultSet rs = null;

		try {
			statement = connection.createStatement();
			statement.executeUpdate(sql, Statement.RETURN_GENERATED_KEYS);
			rs = statement.getGeneratedKeys();
			if (rs.next()) {
				id = rs.getInt(1);
			}
		} catch (SQLException e) {
			System.err.printf("[INSERT 쿼리 오류, %s]\n" + e.getStackTrace() + "\n", sql);
		}

		try {
			if (statement != null) {
				statement.close();
			}

			if (rs != null) {
				rs.close();
			}
		} catch (SQLException e) {
			System.err.println("[INSERT 종료 오류]\n" + e.getStackTrace());
		}

		return id;
	}

	public void close() {
		try {
			if (connection != null) {
				connection.close();
			}
		} catch (SQLException e) {
			System.err.println("[닫기 오류]\n" + e.getStackTrace());
		}
	}

	public void changeBoard(String changeBoardCode) {
		if (changeBoardCode.equals(Factory.getArticleService().getBoard(1).getCode())) {
			Factory.getSession().setCurrentBoard(Factory.getArticleService().getBoard(1));
			System.out.println(Factory.getArticleService().getBoard(1).getName() + " 게시판으로 이동");
		} else if (changeBoardCode.equals(Factory.getArticleService().getBoard(2).getCode())) {
			Factory.getSession().setCurrentBoard(Factory.getArticleService().getBoard(2));
			System.out.println(Factory.getArticleService().getBoard(2).getName() + " 게시판으로 이동");
		} else {
			System.out.println("게시판 변경 실패 사유 : 잘못된 코드 입력");
		}
	}

	// 구현 보완 필요.
	public void detailArticle(String str) {
		System.out.println(selectRow(str));
		Map<String, Object> article = selectRow(str);
		System.out.println("제목 : " + article.get("title"));
		// 아직 member 구현 안했기에 이부분 pass
//		int a = (int) article.get("memberId");
//		System.out.println("작성자 : " + Factory.getMemberService().getMember(a));
		System.out.println("날짜 : " + article.get("regDate"));
		System.out.println("내용 : " + article.get("body"));
	}

	public int delete(String sql) {
		int id = -1;

		// SQL을 적는 문서파일
		Statement statement = null;

		try {
			statement = connection.createStatement();
			statement.executeUpdate(sql, Statement.RETURN_GENERATED_KEYS);
		} catch (SQLException e) {
			System.err.printf("[DELETE 쿼리 오류, %s]\n" + e.getStackTrace() + "\n", sql);
		}

		try {
			if (statement != null) {
				statement.close();
			}
			id = 0;
		} catch (SQLException e) {
			System.err.println("[DELETE 종료 오류]\n" + e.getStackTrace());
		}
		return id;
	}

	public int saveBoard(String sql) {
		int id = -1;
		// SQL을 적는 문서파일
		Statement statement = null;
		// SQL의 실행결과 보고서
		ResultSet rs = null;

		try {
			statement = connection.createStatement();
			statement.executeUpdate(sql, Statement.RETURN_GENERATED_KEYS);
			rs = statement.getGeneratedKeys();
			if (rs.next()) {
				id = rs.getInt(1);
			}
		} catch (SQLException e) {
			System.err.printf("[INSERT 쿼리 오류, %s]\n" + e.getStackTrace() + "\n", sql);
		}

		try {
			if (statement != null) {
				statement.close();
			}

			if (rs != null) {
				rs.close();
			}
		} catch (SQLException e) {
			System.err.println("[INSERT 종료 오류]\n" + e.getStackTrace());
		}
		return id;
	}

	public Board getBoardById(int id) {
		List<Board> boards = Factory.getArticleService().getBoards();
		for (Board b : boards) {
			if (b.getId() == id) {
				return b;
			}
		}
		return null;
	}

	public List<Article> getArticlesByBoardCode(String code) {
		List<Board> boards = Factory.getArticleService().getBoards();
		List<Article> articles = new ArrayList<>();
		Board board = new Board();
		for (Board b : boards) {
			if (b.getCode().equals(code)) {
				board = b;
				break;
			}
		}
		if (board != null) {
			List<Article> tempArticles = Factory.getArticleService().getArticles();
			for (Article a : tempArticles) {
				if (a.getBoardId() == board.getId()) {
					articles.add(a);
				}
			}
		}
		return articles;
	}
}

// Factory
// 프로그램 전체에서 공유되는 객체 리모콘을 보관하는 클래스

class Factory {
	private static Session session;
	private static DB db;
	private static DBConnection dbConnection;
	private static BuildService buildService;
	private static ArticleService articleService;
	private static ArticleDao articleDao;
	private static MemberService memberService;
	private static MemberDao memberDao;
	private static Scanner scanner;

	public static DBConnection getDBConnection() {
		if (dbConnection == null) {
			dbConnection = new DBConnection();
		}
		return dbConnection;
	}
	
	public static DB getDB() {
		if (db == null) {
			db = new DB();
		}
		return db;
	}

	public static Session getSession() {
		if (session == null) {
			session = new Session();
		}
		return session;
	}

	public static Scanner getScanner() {
		if (scanner == null) {
			scanner = new Scanner(System.in);
		}
		return scanner;
	}

	public static ArticleService getArticleService() {
		if (articleService == null) {
			articleService = new ArticleService();
		}
		return articleService;
	}

	public static ArticleDao getArticleDao() {
		if (articleDao == null) {
			articleDao = new ArticleDao();
		}
		return articleDao;
	}

	public static MemberService getMemberService() {
		if (memberService == null) {
			memberService = new MemberService();
		}
		return memberService;
	}

	public static MemberDao getMemberDao() {
		if (memberDao == null) {
			memberDao = new MemberDao();
		}
		return memberDao;
	}

	public static BuildService getBuildService() {
		if (buildService == null) {
			buildService = new BuildService();
		}
		return buildService;
	}
}

// App
class App {
	private Map<String, Controller> controllers;

	// 컨트롤러 만들고 한곳에 정리
	// 나중에 컨트롤러 이름으로 쉽게 찾아쓸 수 있게 하려고 Map 사용
	void initControllers() {
		controllers = new HashMap<>();
		controllers.put("build", new BuildController());
		controllers.put("article", new ArticleController());
		controllers.put("member", new MemberController());
	}

	public App() {
		// 컨트롤러 등록
		initControllers();

		Factory.getDBConnection().connect();

		// 관리자 회원 생성
		Factory.getMemberService().join("admin", "admin", "관리자");

		// 공지사항 게시판 생성
		Factory.getArticleService().makeBoard("공지사항", "notice");
		// 자유 게시판 생성
		Factory.getArticleService().makeBoard("자유게시판", "free");

		// 현재 게시판을 1번 게시판으로 선택
		Factory.getSession().setCurrentBoard(Factory.getArticleService().getBoard(1));
		// 임시 : 현재 로그인 된 회원은 1번 회원으로 지정, 이건 나중에 회원가입, 로그인 추가되면 제거해야함
		Factory.getSession().setLoginedMember(Factory.getMemberService().getMember(1));
	}

	public void start() {
		while (true) {
			System.out.printf("명령어 : ");
			String command = Factory.getScanner().nextLine().trim();

			if (command.length() == 0) {
				continue;
			} else if (command.equals("exit")) {
				break;
			}

			Request reqeust = new Request(command);

			if (reqeust.isValidRequest() == false) {
				continue;
			}

			if (controllers.containsKey(reqeust.getControllerName()) == false) {
				continue;
			}

			controllers.get(reqeust.getControllerName()).doAction(reqeust);
		}

		if (Main.workStarted == true) {
			Main.workStarted = false;
		}
		Factory.getDBConnection().close();
		Factory.getScanner().close();
		return;
	}
}

// Request
class Request {
	private String requestStr;
	private String controllerName;
	private String actionName;
	private String arg1;
	private String arg2;
	private String arg3;

	boolean isValidRequest() {
		return actionName != null;
	}

	Request(String requestStr) {
		this.requestStr = requestStr;
		String[] requestStrBits = requestStr.split(" ");
		this.controllerName = requestStrBits[0];

		if (requestStrBits.length > 1) {
			this.actionName = requestStrBits[1];
		}

		if (requestStrBits.length > 2) {
			this.arg1 = requestStrBits[2];
		}

		if (requestStrBits.length > 3) {
			this.arg2 = requestStrBits[3];
		}

		if (requestStrBits.length > 4) {
			this.arg3 = requestStrBits[4];
		}
	}

	public String getControllerName() {
		return controllerName;
	}

	public void setControllerName(String controllerName) {
		this.controllerName = controllerName;
	}

	public String getActionName() {
		return actionName;
	}

	public void setActionName(String actionName) {
		this.actionName = actionName;
	}

	public String getArg1() {
		return arg1;
	}

	public void setArg1(String arg1) {
		this.arg1 = arg1;
	}

	public String getArg2() {
		return arg2;
	}

	public void setArg2(String arg2) {
		this.arg2 = arg2;
	}

	public String getArg3() {
		return arg3;
	}

	public void setArg3(String arg3) {
		this.arg3 = arg3;
	}
}

// Controller
abstract class Controller {
	abstract void doAction(Request reqeust);
}

class ArticleController extends Controller {
	private ArticleService articleService;

	ArticleController() {
		articleService = Factory.getArticleService();
	}

	public void doAction(Request reqeust) {
		if (reqeust.getActionName().equals("list")) {
			actionList(reqeust);
		} else if (reqeust.getActionName().equals("write")) {
			actionWrite(reqeust);
		} else if (reqeust.getActionName().equals("changeBoard")) {
			actionChangeBoard(reqeust);
		} else if (reqeust.getActionName().equals("detail")) {
			actionDetail(reqeust);
		} else if (reqeust.getActionName().equals("modify")) {
			actionModify(reqeust);
		} else if (reqeust.getActionName().equals("delete")) {
			actionDelete(reqeust);
		} else if (reqeust.getActionName().equals("makeBoard")) {
			actionMakeBoard(reqeust);
		}
	}

	private void actionMakeBoard(Request reqeust) {
		System.out.print("게시판 이름 : ");
		String name = Factory.getScanner().nextLine().trim();
		System.out.print("게시판 코드 : ");
		String code = Factory.getScanner().nextLine().trim();
		if (articleService.makeBoard(name, code) < 0) {
			System.out.println("게시판 생성 실패 사유 : ");
		}
	}

	private void actionDelete(Request reqeust) {
		try {
			int deleteArticleNum = Integer.parseInt(reqeust.getArg1());
			articleService.delete(deleteArticleNum);
		} catch (Exception e) {
			System.out.println("게시물 삭제 실패 사유 : 게시물 번호 미입력");
		}
	}

	private void actionModify(Request reqeust) {
		try {
			int modifyArticleNum = Integer.parseInt(reqeust.getArg1());
			System.out.print("제목 : ");
			String newTitle = Factory.getScanner().nextLine().trim();
			System.out.print("내용 : ");
			String newBody = Factory.getScanner().nextLine().trim();
			articleService.modify(modifyArticleNum, newTitle, newBody);
		} catch (Exception e) {
			System.out.println("게시물 수정 실패 사유 : 게시물 번호 미입력");
		}
	}

	private void actionDetail(Request reqeust) {
		try {
			int detailArticleNum = Integer.parseInt(reqeust.getArg1());
			articleService.detail(detailArticleNum);
		} catch (Exception e) {
			System.out.println("게시물 상세보기 실패 사유 : 게시물 번호 미입력");
		}
	}

	private void actionChangeBoard(Request reqeust) {
		try {
			articleService.changeBoard(reqeust.getArg1());
		} catch (Exception e) {
			System.out.println("게시판 변경 실패 사유 : 코드 미입력");
		}
	}

	private void actionList(Request reqeust) {
		List<Article> articles = articleService.getArticles();

		System.out.println("== 게시물 리스트 시작 ==");
		for (Article article : articles) {
			if (article.getBoardId() == Factory.getSession().getCurrentBoard().getId()) {
				System.out.printf("%d, %s, %s\n", article.getId(), article.getRegDate(), article.getTitle());
			}
		}
		System.out.println("== 게시물 리스트 끝 ==");
	}

	private void actionWrite(Request reqeust) {
		System.out.printf("제목 : ");
		String title = Factory.getScanner().nextLine();
		System.out.printf("내용 : ");
		String body = Factory.getScanner().nextLine();

		// 현재 게시판 id 가져오기
		int boardId = Factory.getSession().getCurrentBoard().getId();

		// 현재 로그인한 회원의 id 가져오기
		int memberId = Factory.getSession().getLoginedMember().getId();
		int newId = articleService.write(boardId, memberId, title, body);

		System.out.printf("%d번 글이 생성되었습니다.\n", newId);
	}
}

class BuildController extends Controller {
	private BuildService buildService;

	BuildController() {
		buildService = Factory.getBuildService();
	}

	@Override
	void doAction(Request reqeust) {
		if (reqeust.getActionName().equals("site")) {
			actionSite();
		} else if (reqeust.getActionName().equals("startAutoSite")) {
			actionStartAutoSite();
		} else if (reqeust.getActionName().equals("stopAutoSite")) {
			actionStopAutoSite();
		}
	}

	private void actionStopAutoSite() {
		buildService.stopAutoSite();
	}

	private void actionStartAutoSite() {
		buildService.startAutoSite();
	}

	private void actionSite() {
		buildService.buildSite();
	}
}

class MemberController extends Controller {
	private MemberService memberService;

	MemberController() {
		memberService = Factory.getMemberService();
	}

	void doAction(Request reqeust) {
		if (reqeust.getActionName().equals("logout")) {
			actionLogout(reqeust);
		} else if (reqeust.getActionName().equals("login")) {
			actionLogin(reqeust);
		} else if (reqeust.getActionName().equals("whoami")) {
			actionWhoami(reqeust);
		} else if (reqeust.getActionName().equals("join")) {
			actionJoin(reqeust);
		}
	}

	private void actionJoin(Request reqeust) {

	}

	private void actionWhoami(Request reqeust) {
		Member loginedMember = Factory.getSession().getLoginedMember();

		if (loginedMember == null) {
			System.out.println("비회원");
		} else {
			System.out.println(loginedMember.getName());
		}

	}

	private void actionLogin(Request reqeust) {
		System.out.printf("로그인 아이디 : ");
		String loginId = Factory.getScanner().nextLine().trim();

		System.out.printf("로그인 비번 : ");
		String loginPw = Factory.getScanner().nextLine().trim();

		Member member = memberService.getMemberByLoginIdAndLoginPw(loginId, loginPw);

		if (member == null) {
			System.out.println("일치하는 회원이 없습니다.");
		} else {
			System.out.println(member.getName() + "님 환영합니다.");
			Factory.getSession().setLoginedMember(member);
		}
	}

	private void actionLogout(Request reqeust) {
		Member loginedMember = Factory.getSession().getLoginedMember();

		if (loginedMember != null) {
			Session session = Factory.getSession();
			System.out.println("로그아웃 되었습니다.");
			session.setLoginedMember(null);
		}

	}
}

// Service
class BuildService {
	ArticleService articleService;

	BuildService() {
		articleService = Factory.getArticleService();
	}

	public void stopAutoSite() {
		Main.workStarted = false;
	}

	public void startAutoSite() {
		Main.workStarted = true;
	}

	public void buildSite() {
		//build site를 한 후, DB에서 데이터를 삭제하면, 해당 데이터의 html은 여전히 남아있다.
//		Util.deleteDir("site");
		Util.makeDir("site");
		Util.makeDir("site/article");

		String head = Util.getFileContents("site_template/part/head.html");
		String foot = Util.getFileContents("site_template/part/foot.html");

		// 각 게시판 별 게시물리스트 페이지 생성
		List<Board> boards = articleService.getBoards();

		for (Board board : boards) {
			String fileName = "list-" + board.getCode() + ".html";

			String html = "";

			List<Article> articles = articleService.getArticlesByBoardCode(board.getCode());

			String template = Util.getFileContents("site_template/article/list.html");

			for (Article article : articles) {
				html += "<tr>";
				html += "<td>" + article.getId() + "</td>";
				html += "<td>" + article.getRegDate() + "</td>";
				html += "<td><a href=\"" + article.getId() + ".html\">" + article.getTitle() + "</a></td>";
				html += "</tr>";
			}

			html = template.replace("${TR}", html);

			html = head + html + foot;

			Util.writeFileContents("site/article/" + fileName, html);
		}

		// 게시물 별 파일 생성
		List<Article> articles = articleService.getArticles();

		for (Article article : articles) {
			String html = "";

			html += "<div>제목 : " + article.getTitle() + "</div>";
			html += "<div>내용 : " + article.getBody() + "</div>";

			// list라서 DB의 id와는 쌓이는 순서가 반대가 되어서 if문의 조건식이 이렇게 되어버렸음.
			// 중간에 삭제할 경우 이어지지 404 페이지가 생기니 html 파일이 존재하지 않을 경우 넘기는 방법 찾아내기.
			if (article.getId() != articles.get(articles.size() - 1).getId()) {
				html += "<div><a href=\"" + (article.getId() - 1) + ".html\">이전글</a></div>";
			}
			if (article.getId() != articles.get(0).getId()) {
				html += "<div><a href=\"" + (article.getId() + 1) + ".html\">다음글</a></div>";
			}

			html = head + html + foot;

			Util.writeFileContents("site/article/" + article.getId() + ".html", html);
		}
	}
}

class ArticleService {
	private ArticleDao articleDao;

	ArticleService() {
		articleDao = Factory.getArticleDao();
	}

	public void delete(int deleteArticleNum) {
		articleDao.delete(deleteArticleNum);
	}

	public void modify(int modifyArticleNum, String newTitle, String newBody) {
		articleDao.modify(modifyArticleNum, newTitle, newBody);
	}

	public void detail(int detailArticleNum) {
		articleDao.detailArticle(detailArticleNum);
	}

	public void changeBoard(String code) {
		articleDao.changeBoard(code);
	}

	// dbConnection 완료
	public List<Article> getArticlesByBoardCode(String code) {
		return articleDao.getArticlesByBoardCode(code);
	}

	// dbConnection 완료
	public List<Board> getBoards() {
		return articleDao.getBoards();
	}

	// dbConnection 완료
	public int makeBoard(String name, String code) {
		return articleDao.saveBoard(name, code);
	}

	// dbConnection 완료
	public Board getBoard(int id) {
		return articleDao.getBoard(id);
	}

	// dbConnection 완료
	public int write(int boardId, int memberId, String title, String body) {
		Article article = new Article(boardId, memberId, title, body);
		return articleDao.save(article);
	}

	// dbConnection 완료
	public List<Article> getArticles() {
		return articleDao.getArticles();
	}
}

class MemberService {
	private MemberDao memberDao;

	MemberService() {
		memberDao = Factory.getMemberDao();
	}

	public Member getMemberByLoginIdAndLoginPw(String loginId, String loginPw) {
		return memberDao.getMemberByLoginIdAndLoginPw(loginId, loginPw);
	}

	public int join(String loginId, String loginPw, String name) {
		Member oldMember = memberDao.getMemberByLoginId(loginId);

		if (oldMember != null) {
			return -1;
		}

		Member member = new Member(loginId, loginPw, name);
		return memberDao.save(member);
	}

	public Member getMember(int id) {
		return memberDao.getMember(id);
	}
}

// Dao
class ArticleDao {
	DB db;
	DBConnection dbConnection;

	ArticleDao() {
		dbConnection = Factory.getDBConnection();
	}

	public boolean isArticleExists(int num) {
		for (Article a : getArticles()) {
			if (a.getId() == num) {
				return true;
			}
		}
		return false;
	}

	public void delete(int deleteArticleNum) {
		if (isArticleExists(deleteArticleNum)) {
			String sql = "DELETE FROM article WHERE id=" + deleteArticleNum;
			if (dbConnection.delete(sql) != -1) {
				System.out.println(deleteArticleNum + "번 게시물 삭제 완료");
			}
		} else {
			System.out.println("게시물 삭제 실패 사유 : 존재하지 않는 게시물 번호");
		}
	}

	public void modify(int modifyArticleNum, String newTitle, String newBody) {
		if (isArticleExists(modifyArticleNum)) {
			String sql = "UPDATE article SET title=\'" + newTitle + "\', `body`=\'" + newBody + "\' WHERE id="
					+ modifyArticleNum;
			if (dbConnection.update(sql) != 0) {
				System.out.println(modifyArticleNum + "번 게시물 수정 완료");
			}
		} else {
			System.out.println("게시물 수정 실패 사유 : 존재하지 않는 게시물 번호");
		}
	}

	public void detailArticle(int detailArticleNum) {
		if (isArticleExists(detailArticleNum)) {
			String str = "SELECT * FROM article WHERE id=" + detailArticleNum;
			dbConnection.detailArticle(str);
		} else {
			System.out.println("게시물 상세보기 실패 사유 : 존재하지 않는 게시물 번호");
		}
	}

	public void changeBoard(String changeBoardCode) {
		if (Factory.getSession().getCurrentBoard().getCode().equals(changeBoardCode)) {
			System.out.println("게시판 변경 실패 사유 : 현재 게시판과 일치");
		} else {
			dbConnection.changeBoard(changeBoardCode);
		}
	}

	public List<Article> getArticlesByBoardCode(String code) {
		return dbConnection.getArticlesByBoardCode(code);
	}

	public List<Board> getBoards() {
		List<Map<String, Object>> rows = dbConnection.selectRows("SELECT * FROM board ORDER BY id DESC");
		List<Board> boards = new ArrayList<>();

		for (Map<String, Object> row : rows) {
			boards.add(new Board(row));
		}
		return boards;
	}

	public int saveBoard(String name, String code) {
		String sql = "";
		if (!isBoardExistByCode(code)) {
			sql = "INSERT INTO board SET regDate=NOW(), `name`='" + name + "', `code`='" + code + "'";
			return dbConnection.saveBoard(sql);
		}
		return -1;
	}

	private boolean isBoardExistByCode(String code) {
		for (Board b : getBoards()) {
			if (b.getCode().equals(code)) {
				return true;
			}
		}
		return false;
	}

	public int save(Article article) {
		String sql = "";
		sql += "INSERT INTO article ";
		sql += String.format("SET regDate = '%s'", article.getRegDate());
		sql += String.format(", title = '%s'", article.getTitle());
		sql += String.format(", `body` = '%s'", article.getBody());
		sql += String.format(", memberId = '%d'", article.getMemberId());
		sql += String.format(", boardId = '%d'", article.getBoardId());

		return dbConnection.insert(sql);
	}

	public Board getBoard(int id) {
		return dbConnection.getBoardById(id);
	}

	public List<Article> getArticles() {
		List<Map<String, Object>> rows = dbConnection.selectRows("SELECT * FROM article ORDER BY id DESC");
		List<Article> articles = new ArrayList<>();

		for (Map<String, Object> row : rows) {
			articles.add(new Article(row));
		}

		return articles;

		// return db.getArticles();
	}

}

class MemberDao {
	DB db;

	MemberDao() {
		db = Factory.getDB();
	}

	public Member getMemberByLoginIdAndLoginPw(String loginId, String loginPw) {
		return db.getMemberByLoginIdAndLoginPw(loginId, loginPw);
	}

	public Member getMemberByLoginId(String loginId) {
		return db.getMemberByLoginId(loginId);
	}

	public Member getMember(int id) {
		return db.getMember(id);
	}

	public int save(Member member) {
		return db.saveMember(member);
	}
}

// DB
class DB {
	private Map<String, Table> tables;

	public DB() {
		String dbDirPath = getDirPath();
		Util.makeDir(dbDirPath);

		tables = new HashMap<>();

		tables.put("article", new Table<Article>(Article.class, dbDirPath));
		tables.put("board", new Table<Board>(Board.class, dbDirPath));
		tables.put("member", new Table<Member>(Member.class, dbDirPath));
	}

	public List<Article> getArticlesByBoardCode(String code) {
		Board board = getBoardByCode(code);
		// free => 2
		// notice => 1

		List<Article> articles = getArticles();
		List<Article> newArticles = new ArrayList<>();

		for (Article article : articles) {
			if (article.getBoardId() == board.getId()) {
				newArticles.add(article);
			}
		}

		return newArticles;
	}

	public Member getMemberByLoginIdAndLoginPw(String loginId, String loginPw) {
		List<Member> members = getMembers();

		for (Member member : members) {
			if (member.getLoginId().equals(loginId) && member.getLoginPw().equals(loginPw)) {
				return member;
			}
		}

		return null;
	}

	public Member getMemberByLoginId(String loginId) {
		List<Member> members = getMembers();

		for (Member member : members) {
			if (member.getLoginId().equals(loginId)) {
				return member;
			}
		}

		return null;
	}

	public List<Member> getMembers() {
		return tables.get("member").getRows();
	}

	public Board getBoardByCode(String code) {
		List<Board> boards = getBoards();

		for (Board board : boards) {
			if (board.getCode().equals(code)) {
				return board;
			}
		}

		return null;
	}

	public List<Board> getBoards() {
		return tables.get("board").getRows();
	}

	public Member getMember(int id) {
		return (Member) tables.get("member").getRow(id);
	}

	public int saveBoard(Board board) {
		return tables.get("board").saveRow(board);
	}

	public String getDirPath() {
		return "db";
	}

	public int saveMember(Member member) {
		return tables.get("member").saveRow(member);
	}

	public Board getBoard(int id) {
		return (Board) tables.get("board").getRow(id);
	}

	public List<Article> getArticles() {
		return tables.get("article").getRows();
	}

	public int saveArticle(Article article) {
		return tables.get("article").saveRow(article);
	}

	public void backup() {
		for (String tableName : tables.keySet()) {
			Table table = tables.get(tableName);
			table.backup();
		}
	}
}

// Table
class Table<T> {
	private Class<T> dataCls;
	private String tableName;
	private String tableDirPath;

	public Table(Class<T> dataCls, String dbDirPath) {
		this.dataCls = dataCls;
		this.tableName = Util.lcfirst(dataCls.getCanonicalName());
		this.tableDirPath = dbDirPath + "/" + this.tableName;

		Util.makeDir(tableDirPath);
	}

	private String getTableName() {
		return tableName;
	}

	public int saveRow(T data) {
		Dto dto = (Dto) data;

		if (dto.getId() == 0) {
			int lastId = getLastId();
			int newId = lastId + 1;
			dto.setId(newId);
			setLastId(newId);
		}

		String rowFilePath = getRowFilePath(dto.getId());

		Util.writeJsonFile(rowFilePath, data);

		return dto.getId();
	};

	private String getRowFilePath(int id) {
		return tableDirPath + "/" + id + ".json";
	}

	private void setLastId(int lastId) {
		String filePath = getLastIdFilePath();
		Util.writeFileContents(filePath, lastId);
	}

	private int getLastId() {
		String filePath = getLastIdFilePath();

		if (Util.isFileExists(filePath) == false) {
			int lastId = 0;
			Util.writeFileContents(filePath, lastId);
			return lastId;
		}

		return Integer.parseInt(Util.getFileContents(filePath));
	}

	private String getLastIdFilePath() {
		return this.tableDirPath + "/lastId.txt";
	}

	public T getRow(int id) {
		return (T) Util.getObjectFromJson(getRowFilePath(id), dataCls);
	}

	public void backup() {

	}

	void delete(int id) {
		/* 구현 */
	};

	List<T> getRows() {
		int lastId = getLastId();

		List<T> rows = new ArrayList<>();

		for (int id = 1; id <= lastId; id++) {
			T row = getRow(id);

			if (row != null) {
				rows.add(row);
			}
		}

		return rows;
	};
}

// DTO
abstract class Dto {
	private int id;
	private String regDate;

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public String getRegDate() {
		return regDate;
	}

	public void setRegDate(String regDate) {
		this.regDate = regDate;
	}

	Dto() {
		this(0);
	}

	Dto(int id) {
		this(id, Util.getNowDateStr());
	}

	Dto(int id, String regDate) {
		this.id = id;
		this.regDate = regDate;
	}
}

class Board extends Dto {
	private String name;
	private String code;

	public Board() {
	}

	public Board(Map<String, Object> row) {
		this.setId((int) (long) row.get("id"));

		String regDate = row.get("regDate") + "";
		this.setRegDate(regDate);
		this.setName((String) row.get("name"));
		this.setCode((String) row.get("code"));
	}

	public Board(String name, String code) {
		this.name = name;
		this.code = code;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getCode() {
		return code;
	}

	public void setCode(String code) {
		this.code = code;
	}

}

class Article extends Dto {
	private int boardId;
	private int memberId;
	private String title;
	private String body;

	public Article() {

	}

	public Article(int boardId, int memberId, String title, String body) {
		this.boardId = boardId;
		this.memberId = memberId;
		this.title = title;
		this.body = body;
	}

	public Article(Map<String, Object> row) {
		this.setId((int) (long) row.get("id"));

		String regDate = row.get("regDate") + "";
		this.setRegDate(regDate);
		this.setTitle((String) row.get("title"));
		this.setBody((String) row.get("body"));
		this.setMemberId((int) (long) row.get("memberId"));
		this.setBoardId((int) (long) row.get("boardId"));
	}

	public int getBoardId() {
		return boardId;
	}

	public void setBoardId(int boardId) {
		this.boardId = boardId;
	}

	public int getMemberId() {
		return memberId;
	}

	public void setMemberId(int memberId) {
		this.memberId = memberId;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getBody() {
		return body;
	}

	public void setBody(String body) {
		this.body = body;
	}

	@Override
	public String toString() {
		return "Article [boardId=" + boardId + ", memberId=" + memberId + ", title=" + title + ", body=" + body
				+ ", getId()=" + getId() + ", getRegDate()=" + getRegDate() + "]";
	}

}

class ArticleReply extends Dto {
	private int id;
	private String regDate;
	private int articleId;
	private int memberId;
	private String body;

	ArticleReply() {

	}

	public int getArticleId() {
		return articleId;
	}

	public void setArticleId(int articleId) {
		this.articleId = articleId;
	}

	public int getMemberId() {
		return memberId;
	}

	public void setMemberId(int memberId) {
		this.memberId = memberId;
	}

	public String getBody() {
		return body;
	}

	public void setBody(String body) {
		this.body = body;
	}

}

class Member extends Dto {
	private String loginId;
	private String loginPw;
	private String name;

	public Member() {

	}

	public Member(String loginId, String loginPw, String name) {
		this.loginId = loginId;
		this.loginPw = loginPw;
		this.name = name;
	}

	public String getLoginId() {
		return loginId;
	}

	public void setLoginId(String loginId) {
		this.loginId = loginId;
	}

	public String getLoginPw() {
		return loginPw;
	}

	public void setLoginPw(String loginPw) {
		this.loginPw = loginPw;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}
}

// Util
class Util {
	// 현재날짜문장
	public static String getNowDateStr() {
		Calendar cal = Calendar.getInstance();
		SimpleDateFormat Date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		String dateStr = Date.format(cal.getTime());
		return dateStr;
	}

	// 파일에 내용쓰기
	public static void writeFileContents(String filePath, int data) {
		writeFileContents(filePath, data + "");
	}

	// 첫 문자 소문자화
	public static String lcfirst(String str) {
		String newStr = "";
		newStr += str.charAt(0);
		newStr = newStr.toLowerCase();

		return newStr + str.substring(1);
	}

	// 파일이 존재하는지
	public static boolean isFileExists(String filePath) {
		File f = new File(filePath);
		if (f.isFile()) {
			return true;
		}

		return false;
	}

	// 파일내용 읽어오기
	public static String getFileContents(String filePath) {
		String rs = null;
		try {
			// 바이트 단위로 파일읽기
			FileInputStream fileStream = null; // 파일 스트림

			fileStream = new FileInputStream(filePath);// 파일 스트림 생성
			// 버퍼 선언
			byte[] readBuffer = new byte[fileStream.available()];
			while (fileStream.read(readBuffer) != -1) {
			}

			rs = new String(readBuffer);

			fileStream.close(); // 스트림 닫기
		} catch (Exception e) {
			e.getStackTrace();
		}

		return rs;
	}

	// 파일 쓰기
	public static void writeFileContents(String filePath, String contents) {
		BufferedOutputStream bs = null;
		try {
			bs = new BufferedOutputStream(new FileOutputStream(filePath));
			bs.write(contents.getBytes()); // Byte형으로만 넣을 수 있음
		} catch (Exception e) {
			e.getStackTrace();
		} finally {
			try {
				bs.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	// Json안에 있는 내용을 가져오기
	public static Object getObjectFromJson(String filePath, Class cls) {
		ObjectMapper om = new ObjectMapper();
		Object obj = null;
		try {
			obj = om.readValue(new File(filePath), cls);
		} catch (JsonParseException e) {
			e.printStackTrace();
		} catch (JsonMappingException e) {
			e.printStackTrace();
		} catch (FileNotFoundException e) {

		} catch (IOException e) {
			e.printStackTrace();
		}

		return obj;
	}

	public static void writeJsonFile(String filePath, Object obj) {
		ObjectMapper om = new ObjectMapper();
		try {
			om.writeValue(new File(filePath), obj);
		} catch (JsonGenerationException e) {
			e.printStackTrace();
		} catch (JsonMappingException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void makeDir(String dirPath) {
		File dir = new File(dirPath);
		if (!dir.exists()) {
			dir.mkdir();
		}
	}
	
//	실패ㅜ
//	public static void deleteDir(String dirPath) {
//		File folder = new File(dirPath);
//		try {
//		    while(folder.exists()) {
//			File[] folder_list = folder.listFiles(); //파일리스트 얻어오기
//					
//			for (int j = 0; j < folder_list.length; j++) {
//				folder_list[j].delete(); //파일 삭제 
//				System.out.println("파일이 삭제되었습니다.");
//						
//			}
//					
//			if(folder_list.length == 0 && folder.isDirectory()){ 
//				folder.delete(); //대상폴더 삭제
//				System.out.println("폴더가 삭제되었습니다.");
//			}
//	            }
//		 } catch (Exception e) {
//			e.getStackTrace();
//		}
//	}
}