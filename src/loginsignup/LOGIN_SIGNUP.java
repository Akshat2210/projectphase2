package loginsignup;

import loginsignup.login.LOGIN;
import loginsignup.portconfig.PortConfig;
import loginsignup.signup.SignUp;
import mainpack.MyClass;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;


public class LOGIN_SIGNUP extends JFrame{
    private JButton LOGINButton;
    public void clickLoginButton() {
        LOGINButton.doClick();
    }

    private JPanel panel;
    private JButton SIGNUPButton;
    private JButton QUITButton;
    private JButton SETPORTCONFIGButton;

    public LOGIN_SIGNUP() {

        SETPORTCONFIGButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                PortConfig portConfig=new PortConfig(LOGIN_SIGNUP.this, false);
                portConfig.setVisible(true);
                setVisible(false);
            }
        });
    }
    public void init(){
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setContentPane(panel);
        setTitle(MyClass.TITLE+": login/signup");

        QUITButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                System.exit(0);
            }
        });
        LOGINButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                MyClass.login=new LOGIN();
                MyClass.login.init();
                MyClass.login.setVisible(true);
                setVisible(false);
            }
        });
        pack();
        SIGNUPButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                MyClass.signUp=new SignUp();
                MyClass.signUp.init();
                MyClass.signUp.setVisible(true);
                setVisible(false);

            }
        });


    }
}
