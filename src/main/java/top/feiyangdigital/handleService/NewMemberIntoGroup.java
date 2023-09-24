package top.feiyangdigital.handleService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMember;
import org.telegram.telegrambots.meta.bots.AbsSender;
import top.feiyangdigital.entity.BaseInfo;
import top.feiyangdigital.entity.GroupInfoWithBLOBs;
import top.feiyangdigital.entity.KeywordsFormat;
import top.feiyangdigital.sqlService.BotRecordService;
import top.feiyangdigital.sqlService.GroupInfoService;
import top.feiyangdigital.utils.SendContent;
import top.feiyangdigital.utils.TimerDelete;
import top.feiyangdigital.utils.groupCaptch.CaptchaManagerCacheMap;
import top.feiyangdigital.utils.groupCaptch.GroupMessageIdCacheMap;
import top.feiyangdigital.utils.groupCaptch.RestrictOrUnrestrictUser;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collector;
import java.util.stream.Collectors;

@Service
public class NewMemberIntoGroup {

    @Autowired
    private GroupInfoService groupInfoService;

    @Autowired
    private BotRecordService botRecordService;

    @Autowired
    private RestrictOrUnrestrictUser restrictOrUnrestrictUser;

    @Autowired
    private SendContent sendContent;

    @Autowired
    private TimerDelete timerDelete;

    @Autowired
    private CaptchaManagerCacheMap captchaManagerCacheMap;

    @Autowired
    private GroupMessageIdCacheMap groupMessageIdCacheMap;


    public void handleMessage(AbsSender sender, Update update, User outUser) {


        Long userId;
        String firstName;
        String lastName;
        Long chatId;
        String groupTitle;
        String joinedTime;
        if (outUser == null) {
            ChatMember member = update.getChatMember().getNewChatMember();
            userId = member.getUser().getId();
            firstName = member.getUser().getFirstName();
            lastName = member.getUser().getLastName();
            chatId = update.getChatMember().getChat().getId();
            groupTitle = update.getChatMember().getChat().getTitle();
            joinedTime = update.getChatMember().getDate().toString();
        } else {
            userId = outUser.getId();
            firstName = outUser.getFirstName();
            lastName = outUser.getLastName();
            chatId = update.getMessage().getChat().getId();
            groupTitle = update.getMessage().getChat().getTitle();
            joinedTime = update.getMessage().getDate().toString();
        }

        if (lastName == null) {
            lastName = "";
        }

        GroupInfoWithBLOBs groupInfoWithBLOBs = groupInfoService.selAllByGroupId(chatId.toString());

        if (groupInfoWithBLOBs != null && "open".equalsIgnoreCase(groupInfoWithBLOBs.getIntogroupusernamecheckflag())) {
            if (StringUtils.hasText(groupInfoWithBLOBs.getKeywords()) && groupInfoWithBLOBs.getKeywords().contains("&&intoGroupBan=")) {
                List<KeywordsFormat> keywordsFormatList = Arrays.stream(groupInfoWithBLOBs.getKeywords().split("\\n{2,}"))
                        .map(String::trim)
                        .map(KeywordsFormat::new)
                        .collect(Collectors.toList());
                for (KeywordsFormat keywordFormat : keywordsFormatList) {
                    Map<String, String> currentMap = keywordFormat.getRuleMap();
                    if (currentMap.containsKey("DelIntoGroupBan")) {
                        String regex = keywordFormat.getRegex();
                        Pattern pattern = Pattern.compile(regex);
                        if (pattern.matcher(firstName).find() || pattern.matcher(lastName).find() || pattern.matcher(firstName + lastName).find()) {
                            restrictOrUnrestrictUser.restrictUser(sender, userId, chatId.toString());
                            KeywordsFormat newKeyFormat = new KeywordsFormat();
                            newKeyFormat.setKeywordsButtons(keywordFormat.getKeywordsButtons());
                            String text = keywordFormat.getReplyText()
                                    .replaceAll("@userId", String.format("<b><a href=\"tg://user?id=%d\">%s</a></b>", userId, firstName))
                                    .replaceAll("@groupName", String.format("<b>%s</b>", groupInfoWithBLOBs.getGroupname()));
                            newKeyFormat.setReplyText(text);
                            newKeyFormat.setKeywordsButtons(Collections.singletonList(keywordFormat.getKeywordsButtons().get(0).replaceAll("@singleUserId", userId.toString())));
                            SendMessage sendMessage = sendContent.createGroupMessage(chatId.toString(), newKeyFormat, "html");
                            sendMessage.setDisableWebPagePreview(true);
                            String msgId = timerDelete.sendTimedMessage(sender, sendMessage, Integer.parseInt(currentMap.get("DelIntoGroupBan")));
                            if (StringUtils.hasText(msgId)){
                                captchaManagerCacheMap.updateUserMapping(userId.toString(), chatId.toString(), 0, Integer.valueOf(msgId));
                            }
                            return;
                        }
                    }
                }
            }
        }

        if (groupInfoWithBLOBs != null && "close".equalsIgnoreCase(groupInfoWithBLOBs.getIntogroupcheckflag()) && "close".equalsIgnoreCase(groupInfoWithBLOBs.getIntogroupwelcomeflag())) {
            botRecordService.addUserRecord(chatId.toString(),userId.toString(),joinedTime);
        }

        if (groupInfoWithBLOBs != null && "close".equalsIgnoreCase(groupInfoWithBLOBs.getIntogroupcheckflag()) && "open".equalsIgnoreCase(groupInfoWithBLOBs.getIntogroupwelcomeflag())) {
            if (groupMessageIdCacheMap.getGroupMessageId(chatId.toString()) != null) {
                timerDelete.deleteByMessageIdImmediately(sender, chatId.toString(), groupMessageIdCacheMap.getGroupMessageId(chatId.toString()));
            }
            if (StringUtils.hasText(groupInfoWithBLOBs.getKeywords()) && groupInfoWithBLOBs.getKeywords().contains("&&welcome=")) {
                List<KeywordsFormat> keywordsFormatList = Arrays.stream(groupInfoWithBLOBs.getKeywords().split("\\n{2,}"))
                        .map(String::trim)
                        .map(KeywordsFormat::new)
                        .collect(Collectors.toList());
                for (KeywordsFormat keywordFormat : keywordsFormatList) {
                    Map<String, String> currentMap = keywordFormat.getRuleMap();
                    if (currentMap.containsKey("DelWelcome")) {
                        KeywordsFormat newKeyFormat = new KeywordsFormat();
                        newKeyFormat.setKeywordsButtons(keywordFormat.getKeywordsButtons());
                        String text = keywordFormat.getReplyText()
                                .replaceAll("@userId", String.format("<b><a href=\"tg://user?id=%d\">%s</a></b>", userId, firstName))
                                .replaceAll("@groupName", String.format("<b>%s</b>", groupInfoWithBLOBs.getGroupname()));
                        newKeyFormat.setReplyText(text);
                        SendMessage sendMessage = sendContent.createGroupMessage(chatId.toString(), newKeyFormat, "html");
                        sendMessage.setDisableWebPagePreview(true);
                        String msgId = timerDelete.sendTimedMessage(sender, sendMessage, Integer.parseInt(currentMap.get("DelWelcome")));
                        groupMessageIdCacheMap.setGroupMessageId(chatId.toString(), Integer.valueOf(msgId));
                    }
                }
            }
            botRecordService.addUserRecord(chatId.toString(),userId.toString(),joinedTime);
        }

        if (groupInfoWithBLOBs != null && "open".equalsIgnoreCase(groupInfoWithBLOBs.getIntogroupcheckflag())) {
            if (groupMessageIdCacheMap.getGroupMessageId(chatId.toString()) != null) {
                timerDelete.deleteByMessageIdImmediately(sender, chatId.toString(), groupMessageIdCacheMap.getGroupMessageId(chatId.toString()));
            }
            String url = String.format("https://t.me/%s?start=_intoGroupInfo%sand%s", BaseInfo.getBotName(), chatId.toString(), userId.toString());
            restrictOrUnrestrictUser.restrictUser(sender, userId, chatId.toString());
            KeywordsFormat keywordsFormat = new KeywordsFormat();
            List<String> keywordsButtons = new ArrayList<>();
            keywordsButtons.add("👥管理员解封##adminUnrestrict" + userId);
            keywordsButtons.add("❗️点击验证$$" + url);
            keywordsFormat.setKeywordsButtons(keywordsButtons);
            String text = String.format("欢迎 <b><a href=\"tg://user?id=%d\">%s</a></b> 加入<b> %s </b>, 现在你需要在<b>90秒内</b>点击下面的验证按钮完成验证，超时将永久限制发言！", userId, firstName, groupTitle);
            keywordsFormat.setReplyText(text);
            try {
                Message message1 = sender.execute(sendContent.createResponseMessage(update, keywordsFormat, "html"));
                Integer messageId = message1.getMessageId();
                captchaManagerCacheMap.updateUserMapping(userId.toString(), chatId.toString(), 0, messageId);
                String text1 = String.format("用户 <b><a href=\"tg://user?id=%d\">%s</a></b> 在 <b>90秒内</b> 未进行验证，永久限制发言！", userId, firstName);
                SendMessage notification = new SendMessage();
                notification.setChatId(chatId);
                notification.setText(text1);
                notification.setParseMode(ParseMode.HTML);
                timerDelete.deleteMessageAndNotifyAfterDelay(sender, notification, chatId.toString(), messageId, 90, userId, 20);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

}
