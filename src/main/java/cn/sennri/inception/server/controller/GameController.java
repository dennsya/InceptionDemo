package cn.sennri.inception.server.controller;

import cn.sennri.inception.player.BasePlayer;
import cn.sennri.inception.player.Player;
import cn.sennri.inception.server.Game;
import cn.sennri.inception.util.IpAddressUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Sennri
 */
@SuppressWarnings("AlibabaRemoveCommentedCode")
@RequestMapping("/game")
@RestController
public class GameController {
    private static final Logger logger = LogManager.getLogger(GameController.class.getName());
    Game game;

    CyclicBarrier gameEndBarrier;

    AtomicInteger deck = new AtomicInteger(1);

    public GameController() throws UnknownHostException {
    }

    @RequestMapping(value = "/start", method = RequestMethod.POST)
    void test() throws BrokenBarrierException, InterruptedException {
        logger.info("game start");
        gameEndBarrier = new CyclicBarrier(2);
        game = new Game(new ArrayList<>());
        while (deck.get() > 0){
            TimeUnit.SECONDS.sleep(1);
            logger.info("draw phase");
            gameEndBarrier.await();
            logger.info("draw phase");
            gameEndBarrier.await();
            logger.info("draw phase");
            gameEndBarrier.await();
        }
        logger.info("game finish");
    }

    Player testPlayer = new BasePlayer(InetAddress.getLocalHost());

    @RequestMapping(value = "/trigger", method = RequestMethod.POST)
    void test2(HttpServletRequest request, HttpServletResponse response)
            throws BrokenBarrierException, InterruptedException, UnknownHostException {
        String ipAddress = IpAddressUtil.getIpAddress(request);
        InetAddress address = InetAddress.getByName(ipAddress);
        if (testPlayer.getInetAddress().equals(address)){
            gameEndBarrier.await();
            logger.info("trigger");
        }else{
            logger.warn("you are not the player of this turn.");
        }
    }

    @RequestMapping(value = "/draw", method = RequestMethod.POST)
    void draw(HttpServletRequest request, HttpServletResponse response)
            throws BrokenBarrierException, InterruptedException, UnknownHostException {
        String ipAddress = IpAddressUtil.getIpAddress(request);
        InetAddress address = InetAddress.getByName(ipAddress);
        if (testPlayer.getInetAddress().equals(address)){
            logger.info("deck decrement:" + deck.decrementAndGet());
        }else{
            logger.warn("you are not the player of this turn.");
        }
    }

    final static int MIN_PLAYER = 2;
    final static int MAX_PLAYER = 3;
    ThreadPoolExecutor executor = new ThreadPoolExecutor(MAX_PLAYER, MAX_PLAYER, 10, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(10),
            new ThreadPoolExecutor.DiscardPolicy());

    List<Player> playerList;

    AtomicBoolean lobbyBusy;

    final Map<InetAddress, AtomicBoolean> addressMap = new ConcurrentHashMap<>(MAX_PLAYER);

    /**
     * 应该设置为异步方法
     */
    void createLobby(){
        if (lobbyBusy != null){
            logger.warn("Lobby already exists");
            return;
        }
        lobbyBusy = new AtomicBoolean(false);
        // 若没有被quit
        while (lobbyBusy != null){
            boolean allClear = false;
            while (!allClear){
                allClear = true;
                for(AtomicBoolean ready:addressMap.values()){
                    if (!ready.get()){
                        allClear = false;
                        break;
                    }
                }
                try {
                    TimeUnit.MILLISECONDS.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            lobbyBusy.set(true);

            List<InetAddress> list = new ArrayList<>(addressMap.keySet());
            Collections.shuffle(list);

            game = new Game(list);
            CompletableFuture.runAsync(new Runnable() {
                @Override
                public void run() {
                    try {
                        game.start();
                    } catch (BrokenBarrierException | InterruptedException | ExecutionException e) {
                        e.printStackTrace();
                    }
                    lobbyBusy.set(false);
                }
            });
        }
    }




    //        executor.submit(new Runnable() {
//            @Override
//            public void run() {
//                boolean allClear = false;
//                while (!allClear){
//                    allClear = true;
//                    for(Player player:playerList){
//                        if (!player.isReady().get()){
//                            allClear = false;
//                            break;
//                        }
//                    }
//                    try {
//                        TimeUnit.MILLISECONDS.sleep(500);
//                    } catch (InterruptedException e) {
//                        e.printStackTrace();
//                    }
//                }
//            }
//        });

    /**
     * 进入房间
     */
    void enterLobby(String ipAddress) throws UnknownHostException {
        InetAddress ip = InetAddress.getByName(ipAddress);
        if (lobbyBusy != null && !lobbyBusy.get()){
            if (addressMap.put(ip, new AtomicBoolean(false)) == null){
                push();
            }
        }
    }

    /**
     * 某个用户退出房间
     * 设计上应该在等待阶段
     */
    void quitLobby(String ipAddress){
        InetAddress ip;
        try {
            ip = InetAddress.getByName(ipAddress);
            if (addressMap.remove(ip) != null){
                // 如果是在游戏中退出的话
                lobbyBusy.getAndSet(false);
                push();
            }
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
    }

    /**
     * 退出房间
     * 设计上应该在等待阶段
     */
    void dissolveLobby(){
        lobbyBusy = null;
        addressMap.clear();
        playerList = null;
        push();
    }


    /**
     * 游戏终止信号
     * 当前发动的卡信息（一个stack）
     * 当前回合玩家
     * 是否为当前响应玩家
     * 效果发动、效果结算、回合转移时、响应玩家转移时、玩家主动离开时、应当push
     * push的是事件的发生或者事件的结果。
     * 因此push应该分为几种类型：宣告发生帧，结果告知帧。
     * 在每个push完成后，需要依次进行askclient，判断是否需要
     */
    void push(){

    }




    /**
     * 向client依次发出请求，确认其是否应答效果
     */
    void askClient(){

    }

}
