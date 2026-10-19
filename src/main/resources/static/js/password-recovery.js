// Independent recovery flow; passwords and reset permissions stay in memory only.
export function recoveryPages({api, layout, bindForm, emailField, passwordField, notice}) {
    let challenge=null, resetToken=null, email='', sentAt=0;
    function request() {
        challenge=null; resetToken=null;
        layout('Восстановить пароль','Отправим шестизначный код на почту вашей учётной записи.',
            '<form>'+emailField+'<button type="submit">Отправить код ↗</button></form>');
        bindForm(async data=>{
            challenge=await api('/auth/recovery/request',data);
            email=data.email; sentAt=Date.now(); location.hash='recovery-code';
        });
    }
    function code() {
        if(!challenge){location.hash='recovery';return;}
        layout('Подтвердите код','Введите код из письма. Он действует 10 минут. Проверьте папку «Спам».',
            '<form><label>Код из письма<input name="code" inputmode="numeric" autocomplete="one-time-code" pattern="[0-9]{6}" maxlength="6" required autofocus></label><button type="submit">Проверить код ↗</button></form><div class="form-bottom"><button id="recovery-resend" class="text-button" type="button">Отправить код повторно</button><a href="#recovery">Начать заново</a></div>');
        notice(challenge.message,true);
        bindForm(async data=>{
            const result=await api('/auth/recovery/verify',{challengeId:challenge.challengeId,code:data.code});
            resetToken=result.resetToken; location.hash='recovery-password';
        });
        const button=document.querySelector('#recovery-resend');
        button.addEventListener('click',async()=>{
            const wait=Math.ceil((sentAt+60000-Date.now())/1000);
            if(wait>0){notice('Повторная отправка через '+wait+' с.');return;}
            button.disabled=true;
            try{challenge=await api('/auth/recovery/request',{email});sentAt=Date.now();resetToken=null;notice('Новый код отправлен. Предыдущий код больше не действует.',true);}
            catch(e){notice(e.message);}finally{button.disabled=false;}
        });
    }
    function password() {
        if(!challenge || !resetToken){location.hash='recovery';return;}
        layout('Новый пароль','Почта подтверждена. Придумайте новый пароль.',
            '<form>'+passwordField('password','Новый пароль','new-password')+passwordField('confirm','Повторите пароль','new-password')+'<button type="submit">Сохранить пароль ↗</button></form>');
        bindForm(async data=>{
            if(data.password!==data.confirm)throw new Error('Пароли не совпадают.');
            if(new TextEncoder().encode(data.password).length>72)throw new Error('Пароль должен занимать не более 72 байт UTF-8.');
            await api('/auth/recovery/reset',{challengeId:challenge.challengeId,resetToken,password:data.password});
            challenge=null;resetToken=null;email='';
            location.hash='login';
            window.addEventListener('hashchange',()=>notice('Пароль изменён. Войдите с новым паролем.',true),{once:true});
        });
    }
    return {recovery:request,'recovery-code':code,'recovery-password':password,forgot:request};
}
